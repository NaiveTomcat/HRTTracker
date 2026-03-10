package cn.naivetomcat.hrt_tracker.pk

/**
 * 给药途径枚举
 */
enum class Route {
    INJECTION,      // 肌肉注射
    ORAL,          // 口服
    SUBLINGUAL,    // 舌下
    GEL,           // 凝胶
    PATCH_APPLY,   // 应用贴片
    PATCH_REMOVE,  // 移除贴片
    ANTIANDROGEN   // 抗雄激素药物（口服，不参与药代动力学计算）
}
