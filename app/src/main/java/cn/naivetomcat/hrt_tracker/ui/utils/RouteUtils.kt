package cn.naivetomcat.hrt_tracker.ui.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import cn.naivetomcat.hrt_tracker.R
import cn.naivetomcat.hrt_tracker.pk.Route
import cn.naivetomcat.hrt_tracker.ui.icons.TablerGenderAndrogyne

/**
 * 根据给药途径获取对应的图标
 */
fun getRouteIcon(route: Route): ImageVector {
    return when (route) {
        Route.INJECTION -> Icons.Filled.Vaccines       // 注射
        Route.ORAL -> Icons.Filled.Medication          // 口服
        Route.SUBLINGUAL -> Icons.Filled.WaterDrop     // 舌下
        Route.GEL -> Icons.Filled.Soap                 // 凝胶
        Route.PATCH_APPLY -> Icons.Filled.AddBox       // 应用贴片
        Route.PATCH_REMOVE -> Icons.Filled.RemoveCircle // 移除贴片
        Route.ANTIANDROGEN -> TablerGenderAndrogyne    // 抗雄激素
    }
}

/**
 * 获取给药途径的显示名称
 */
@Composable
fun getRouteDisplayName(route: Route): String {
    return when (route) {
        Route.INJECTION -> stringResource(R.string.route_injection)
        Route.ORAL -> stringResource(R.string.route_oral)
        Route.SUBLINGUAL -> stringResource(R.string.route_sublingual)
        Route.GEL -> stringResource(R.string.route_gel)
        Route.PATCH_APPLY -> stringResource(R.string.route_patch_apply)
        Route.PATCH_REMOVE -> stringResource(R.string.route_patch_remove)
        Route.ANTIANDROGEN -> stringResource(R.string.route_antiandrogen)
    }
}
