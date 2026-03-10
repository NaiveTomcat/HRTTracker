package cn.naivetomcat.hrt_tracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cn.naivetomcat.hrt_tracker.R
import cn.naivetomcat.hrt_tracker.pk.AntiAndrogen
import cn.naivetomcat.hrt_tracker.pk.DoseEvent
import cn.naivetomcat.hrt_tracker.pk.Ester
import cn.naivetomcat.hrt_tracker.pk.Route
import cn.naivetomcat.hrt_tracker.ui.theme.HRTTrackerTheme
import cn.naivetomcat.hrt_tracker.ui.utils.getRouteDisplayName
import cn.naivetomcat.hrt_tracker.ui.utils.getRouteIcon
import java.text.SimpleDateFormat
import java.util.*

/**
 * 用药记录列表项
 * 
 * @param medicationName 药品名称
 * @param route 给药途径
 * @param doseMG 剂量（mg）
 * @param timeH 时间（小时）
 * @param isAntiAndrogen 是否为抗雄激素药物（影响容器颜色）
 * @param modifier Modifier
 * @param onClick 点击回调
 */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun MedicationRecordItem(
    medicationName: String,
    route: Route,
    doseMG: Double,
    timeH: Double,
    isAntiAndrogen: Boolean = false,
    is24Hour: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val containerColor = if (isAntiAndrogen) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        elevation = CardDefaults.elevatedCardElevation(),
        onClick = onClick ?: {}
    ) {
        ListItem(
            colors = ListItemDefaults.colors(
                containerColor = containerColor,
                leadingIconColor = MaterialTheme.colorScheme.tertiary,
            ),
            overlineContent = {
                Text(
                    text = getRouteDisplayName(route),
                    style = MaterialTheme.typography.labelSmall
                )
            },
            headlineContent = {
                Text(
                    text = "$medicationName · ${formatDose(doseMG)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            },
            leadingContent = {
                Icon(
                    imageVector = getRouteIcon(route),
                    contentDescription = getRouteDisplayName(route),
                    modifier = Modifier.size(40.dp)
                )
            },
            trailingContent = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = formatTime(timeH, is24Hour),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatDate(timeH),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        )
    }
}

/**
 * 格式化剂量显示
 */
private fun formatDose(doseMG: Double): String {
    return if (doseMG >= 1.0) {
        String.format("%.1f %s", doseMG, "mg")
    } else {
        String.format("%.2f %s", doseMG, "mg")
    }
}

/**
 * 格式化时间显示
 */
private fun formatTime(timeH: Double, is24Hour: Boolean): String {
    val milliseconds = (timeH * 3600 * 1000).toLong()
    val date = Date(milliseconds)
    val pattern = if (is24Hour) "HH:mm" else "hh:mm a"
    val sdf = SimpleDateFormat(pattern, Locale.getDefault())
    return sdf.format(date)
}

/**
 * 格式化日期显示（MM/dd）
 */
private fun formatDate(timeH: Double): String {
    val milliseconds = (timeH * 3600 * 1000).toLong()
    val date = Date(milliseconds)
    val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
    return sdf.format(date)
}

/**
 * 从DoseEvent创建列表项
 */
@Composable
fun MedicationRecordItem(
    event: DoseEvent,
    is24Hour: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val isAntiAndrogen = event.route == Route.ANTIANDROGEN
    val medicationName = if (isAntiAndrogen) {
        getAntiAndrogenDisplayName(
            event.extras[DoseEvent.ExtraKey.ANTI_ANDROGEN_TYPE]?.toInt()?.let {
                AntiAndrogen.values().getOrElse(it) { AntiAndrogen.CPA }
            } ?: AntiAndrogen.CPA
        )
    } else {
        getMedicationDisplayName(event.ester)
    }
    
    MedicationRecordItem(
        medicationName = medicationName,
        route = event.route,
        doseMG = event.doseMG,
        timeH = event.timeH,
        isAntiAndrogen = isAntiAndrogen,
        is24Hour = is24Hour,
        modifier = modifier,
        onClick = onClick
    )
}

/**
 * 获取药品显示名称（雌激素）
 */
@Composable
private fun getMedicationDisplayName(ester: Ester): String {
    return when (ester) {
        Ester.E2 -> stringResource(R.string.ester_e2)
        Ester.EB -> stringResource(R.string.ester_eb)
        Ester.EV -> stringResource(R.string.ester_ev)
        Ester.EC -> stringResource(R.string.ester_ec)
        Ester.EN -> stringResource(R.string.ester_en)
    }
}

/**
 * 获取抗雄药物显示名称
 */
@Composable
internal fun getAntiAndrogenDisplayName(antiAndrogen: AntiAndrogen): String {
    return when (antiAndrogen) {
        AntiAndrogen.CPA -> stringResource(R.string.antiandrogen_cpa)
        AntiAndrogen.MPA -> stringResource(R.string.antiandrogen_mpa)
        AntiAndrogen.BICALUTAMIDE -> stringResource(R.string.antiandrogen_bicalutamide)
        AntiAndrogen.SPIRONOLACTONE -> stringResource(R.string.antiandrogen_spironolactone)
    }
}

// ============================================================================
// Previews
// ============================================================================

@Preview(name = "口服雌二醇", showBackground = true, showSystemUi = false)
@Composable
private fun PreviewMedicationRecordItemOral() {
    HRTTrackerTheme {
        Surface {
            MedicationRecordItem(
                medicationName = "雌二醇",
                route = Route.ORAL,
                doseMG = 2.0,
                timeH = System.currentTimeMillis() / 3600000.0,
                modifier = Modifier.padding(16.dp),
                onClick = {}
            )
        }
    }
}

@Preview(name = "注射戊酸雌二醇", showBackground = true)
@Composable
private fun PreviewMedicationRecordItemInjection() {
    HRTTrackerTheme {
        Surface {
            MedicationRecordItem(
                medicationName = "戊酸雌二醇",
                route = Route.INJECTION,
                doseMG = 5.0,
                timeH = System.currentTimeMillis() / 3600000.0,
                modifier = Modifier.padding(16.dp),
                onClick = {}
            )
        }
    }
}

@Preview(name = "舌下含服", showBackground = true)
@Composable
private fun PreviewMedicationRecordItemSublingual() {
    HRTTrackerTheme {
        Surface {
            MedicationRecordItem(
                medicationName = "雌二醇",
                route = Route.SUBLINGUAL,
                doseMG = 1.0,
                timeH = System.currentTimeMillis() / 3600000.0,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(name = "透皮凝胶", showBackground = true)
@Composable
private fun PreviewMedicationRecordItemGel() {
    HRTTrackerTheme {
        Surface {
            MedicationRecordItem(
                medicationName = "雌二醇",
                route = Route.GEL,
                doseMG = 0.75,
                timeH = System.currentTimeMillis() / 3600000.0,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(name = "应用贴片", showBackground = true)
@Composable
private fun PreviewMedicationRecordItemPatchApply() {
    HRTTrackerTheme {
        Surface {
            MedicationRecordItem(
                medicationName = "雌二醇",
                route = Route.PATCH_APPLY,
                doseMG = 2.0,
                timeH = System.currentTimeMillis() / 3600000.0,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(name = "移除贴片", showBackground = true)
@Composable
private fun PreviewMedicationRecordItemPatchRemove() {
    HRTTrackerTheme {
        Surface {
            MedicationRecordItem(
                medicationName = "雌二醇",
                route = Route.PATCH_REMOVE,
                doseMG = 0.0,
                timeH = System.currentTimeMillis() / 3600000.0,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(name = "抗雄激素 (比卡鲁胺)", showBackground = true)
@Composable
private fun PreviewMedicationRecordItemAntiAndrogen() {
    HRTTrackerTheme {
        Surface {
            MedicationRecordItem(
                medicationName = "比卡鲁胺",
                route = Route.ANTIANDROGEN,
                doseMG = 25.0,
                timeH = System.currentTimeMillis() / 3600000.0,
                isAntiAndrogen = true,
                modifier = Modifier.padding(16.dp),
                onClick = {}
            )
        }
    }
}

@Preview(name = "小剂量显示", showBackground = true)
@Composable
private fun PreviewMedicationRecordItemSmallDose() {
    HRTTrackerTheme {
        Surface {
            MedicationRecordItem(
                medicationName = "雌二醇",
                route = Route.SUBLINGUAL,
                doseMG = 0.25,
                timeH = System.currentTimeMillis() / 3600000.0,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Preview(name = "用药记录列表", showBackground = true)
@Composable
private fun PreviewMedicationRecordList() {
    HRTTrackerTheme {
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val currentTime = System.currentTimeMillis() / 3600000.0
                
                MedicationRecordItem(
                    medicationName = "戊酸雌二醇",
                    route = Route.INJECTION,
                    doseMG = 5.0,
                    timeH = currentTime - 168.0,
                    onClick = {}
                )
                
                MedicationRecordItem(
                    medicationName = "雌二醇",
                    route = Route.ORAL,
                    doseMG = 2.0,
                    timeH = currentTime - 12.0,
                    onClick = {}
                )
                
                MedicationRecordItem(
                    medicationName = "比卡鲁胺",
                    route = Route.ANTIANDROGEN,
                    doseMG = 25.0,
                    timeH = currentTime - 6.0,
                    isAntiAndrogen = true,
                    onClick = {}
                )
                
                MedicationRecordItem(
                    medicationName = "雌二醇",
                    route = Route.GEL,
                    doseMG = 0.75,
                    timeH = currentTime,
                    onClick = {}
                )
            }
        }
    }
}

@Preview(name = "使用DoseEvent", showBackground = true)
@Composable
private fun PreviewMedicationRecordItemFromEvent() {
    HRTTrackerTheme {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val currentTime = System.currentTimeMillis() / 3600000.0
                
                MedicationRecordItem(
                    event = DoseEvent(
                        route = Route.ORAL,
                        timeH = currentTime,
                        doseMG = 2.0,
                        ester = Ester.E2
                    ),
                    onClick = {}
                )
                
                MedicationRecordItem(
                    event = DoseEvent(
                        route = Route.ANTIANDROGEN,
                        timeH = currentTime - 6.0,
                        doseMG = 25.0,
                        ester = Ester.E2,
                        extras = mapOf(DoseEvent.ExtraKey.ANTI_ANDROGEN_TYPE to AntiAndrogen.BICALUTAMIDE.ordinal.toDouble())
                    ),
                    onClick = {}
                )
                
                MedicationRecordItem(
                    event = DoseEvent(
                        route = Route.INJECTION,
                        timeH = currentTime - 168.0,
                        doseMG = 5.0,
                        ester = Ester.EV
                    ),
                    onClick = {}
                )
            }
        }
    }
}
