package cn.naivetomcat.hrt_tracker.wear

import android.content.Context
import org.json.JSONArray

data class WearPlan(
    val id: String,
    val name: String,
    val doseMG: Double
)

data class WearDashboard(
    val plans: List<WearPlan>,
    val currentConcentration: Double?,
    val curveValues: List<Float>,
    val updatedAt: Long
) {
    fun concentrationAt(nowMillis: Long): Double? {
        if (curveValues.isEmpty() || updatedAt <= 0L) {
            return currentConcentration
        }
        val centerIndex = curveValues.lastIndex / 2.0
        val elapsedHours = (nowMillis - updatedAt) / 3_600_000.0
        val position = (centerIndex + elapsedHours)
            .coerceIn(0.0, curveValues.lastIndex.toDouble())
        val lower = position.toInt()
        val upper = minOf(lower + 1, curveValues.lastIndex)
        val ratio = position - lower
        return curveValues[lower] +
            (curveValues[upper] - curveValues[lower]) * ratio
    }

    fun currentCurvePosition(nowMillis: Long): Float {
        if (curveValues.size <= 1 || updatedAt <= 0L) return 0.5f
        val centerIndex = curveValues.lastIndex / 2.0
        val elapsedHours = (nowMillis - updatedAt) / 3_600_000.0
        return ((centerIndex + elapsedHours) / curveValues.lastIndex)
            .coerceIn(0.0, 1.0)
            .toFloat()
    }
}

object WearPlanStore {
    private const val PREFERENCES_NAME = "wear_plans"
    private const val KEY_PLANS_JSON = "plans_json"
    private const val KEY_LAST_SENT_PLAN_ID = "last_sent_plan_id"
    private const val KEY_LAST_SENT_AT = "last_sent_at"
    private const val KEY_LAST_REQUESTED_AT = "last_requested_at"
    private const val KEY_CURRENT_CONCENTRATION = "current_concentration"
    private const val KEY_CURVE_VALUES = "curve_values"
    private const val KEY_UPDATED_AT = "updated_at"

    fun saveDashboard(
        context: Context,
        plansJson: String,
        currentConcentration: Double?,
        curveValues: FloatArray,
        updatedAt: Long
    ) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PLANS_JSON, plansJson)
            .apply {
                if (currentConcentration != null) {
                    putString(
                        KEY_CURRENT_CONCENTRATION,
                        currentConcentration.toString()
                    )
                } else {
                    remove(KEY_CURRENT_CONCENTRATION)
                }
                putString(
                    KEY_CURVE_VALUES,
                    curveValues.joinToString(",")
                )
                putLong(KEY_UPDATED_AT, updatedAt)
            }
            .apply()
    }

    fun getDashboard(context: Context): WearDashboard {
        val preferences = context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        val raw = preferences.getString(KEY_PLANS_JSON, "[]") ?: "[]"

        val plans = runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until minOf(array.length(), 2)) {
                    val item = array.getJSONObject(index)
                    add(
                        WearPlan(
                            id = item.getString("id"),
                            name = item.getString("name"),
                            doseMG = item.getDouble("doseMG")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
        return WearDashboard(
            plans = plans,
            currentConcentration = preferences
                .getString(KEY_CURRENT_CONCENTRATION, null)
                ?.toDoubleOrNull(),
            curveValues = preferences
                .getString(KEY_CURVE_VALUES, null)
                ?.split(',')
                ?.mapNotNull { it.toFloatOrNull() }
                .orEmpty(),
            updatedAt = preferences.getLong(KEY_UPDATED_AT, 0L)
        )
    }

    fun getPlans(context: Context): List<WearPlan> =
        getDashboard(context).plans

    fun markSent(context: Context, planId: String, sentAt: Long) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_SENT_PLAN_ID, planId)
            .putLong(KEY_LAST_SENT_AT, sentAt)
            .apply()
    }

    fun recentSentPlanId(
        context: Context,
        nowMillis: Long,
        maxAgeMillis: Long
    ): String? {
        val preferences = context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        val sentAt = preferences.getLong(KEY_LAST_SENT_AT, 0L)
        if (nowMillis - sentAt !in 0..maxAgeMillis) return null
        return preferences.getString(KEY_LAST_SENT_PLAN_ID, null)
    }

    fun clearSentFeedback(context: Context) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_SENT_PLAN_ID)
            .remove(KEY_LAST_SENT_AT)
            .apply()
    }

    fun shouldRequestPlans(context: Context, nowMillis: Long): Boolean {
        val preferences = context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )
        val lastRequestedAt = preferences.getLong(KEY_LAST_REQUESTED_AT, 0L)
        if (nowMillis - lastRequestedAt < 15_000L) return false
        markPlansRequested(context, nowMillis)
        return true
    }

    fun markPlansRequested(context: Context, nowMillis: Long) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_REQUESTED_AT, nowMillis)
            .apply()
    }
}
