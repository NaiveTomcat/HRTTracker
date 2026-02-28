package cn.naivetomcat.hrt_tracker.navigation

/**
 * 应用导航路由
 */
enum class Screen(val route: String, val title: String) {
    HOME("home", "主页"),
    RECORDS("records", "用药记录")
}
