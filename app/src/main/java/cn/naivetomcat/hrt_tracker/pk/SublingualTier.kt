package cn.naivetomcat.hrt_tracker.pk

/**
 * 舌下给药行为档位
 * 用于表示不同的含服时长和黏膜吸收比例
 */
enum class SublingualTier {
    QUICK,      // 快速：约2分钟含服
    CASUAL,     // 随意：约5分钟含服
    STANDARD,   // 标准：约10分钟含服
    STRICT      // 严格：约15分钟含服
}
