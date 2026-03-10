package cn.naivetomcat.hrt_tracker.pk

/**
 * 抗雄激素药物类型枚举
 * 抗雄药物不参与药代动力学计算，仅用于记录和提醒
 */
enum class AntiAndrogen {
    CPA,          // 醋酸环丙孕酮 (Cyproterone Acetate)
    MPA,          // 醋酸甲羟孕酮 (Medroxyprogesterone Acetate)
    BICALUTAMIDE, // 比卡鲁胺 (Bicalutamide)
    SPIRONOLACTONE // 螺内酯 (Spironolactone)
}

/**
 * AntiAndrogen扩展属性：显示名称
 */
val AntiAndrogen.displayName: String
    get() = when (this) {
        AntiAndrogen.CPA -> "醋酸环丙孕酮"
        AntiAndrogen.MPA -> "醋酸甲羟孕酮"
        AntiAndrogen.BICALUTAMIDE -> "比卡鲁胺"
        AntiAndrogen.SPIRONOLACTONE -> "螺内酯"
    }
