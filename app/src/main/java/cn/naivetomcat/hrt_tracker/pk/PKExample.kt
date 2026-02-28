package cn.naivetomcat.hrt_tracker.pk

/**
 * 药代动力学模块使用示例
 * 
 * 本示例展示如何使用PK模块进行雌二醇药代动力学模拟
 */
object PKExample {

    /**
     * 示例1：单次口服雌二醇
     */
    fun example1_SingleOralE2() {
        println("=== 示例1：单次口服2mg雌二醇 ===")
        
        // 创建给药事件
        val event = DoseEvent(
            route = Route.ORAL,
            timeH = 0.0,  // 在时间0给药
            doseMG = 2.0,
            ester = Ester.E2
        )
        
        // 运行模拟
        val result = SimulationEngine.runSimulation(
            events = listOf(event),
            bodyWeightKG = 60.0
        )
        
        // 输出结果
        println("总AUC: ${result.auc} pg·h/mL")
        println("最大浓度: ${result.concPGmL.maxOrNull()} pg/mL")
        println("时间范围: ${result.timeH.first()} - ${result.timeH.last()} h")
        
        // 查询特定时间点的浓度
        val conc2h = result.concentration(2.0)
        println("2小时时浓度: $conc2h pg/mL")
    }

    /**
     * 示例2：戊酸雌二醇肌肉注射
     */
    fun example2_InjectionEV() {
        println("\n=== 示例2：5mg戊酸雌二醇肌肉注射 ===")
        
        val event = DoseEvent(
            route = Route.INJECTION,
            timeH = 0.0,
            doseMG = 5.0,
            ester = Ester.EV
        )
        
        val result = SimulationEngine.runSimulation(
            events = listOf(event),
            bodyWeightKG = 60.0
        )
        
        println("总AUC: ${result.auc} pg·h/mL")
        println("最大浓度: ${result.concPGmL.maxOrNull()} pg/mL")
        
        // 查询不同时间点的浓度
        listOf(24.0, 72.0, 168.0).forEach { hours ->
            val conc = result.concentration(hours)
            println("${hours}小时时浓度: $conc pg/mL")
        }
    }

    /**
     * 示例3：舌下给药
     */
    fun example3_Sublingual() {
        println("\n=== 示例3：1mg雌二醇舌下给药（标准档位） ===")
        
        val event = DoseEvent(
            route = Route.SUBLINGUAL,
            timeH = 0.0,
            doseMG = 1.0,
            ester = Ester.E2,
            extras = mapOf(
                DoseEvent.ExtraKey.SUBLINGUAL_TIER to 2.0  // 2 = STANDARD
            )
        )
        
        val result = SimulationEngine.runSimulation(
            events = listOf(event),
            bodyWeightKG = 60.0
        )
        
        println("总AUC: ${result.auc} pg·h/mL")
        println("最大浓度: ${result.concPGmL.maxOrNull()} pg/mL")
        
        // 查询峰值时间附近的浓度
        listOf(0.5, 1.0, 2.0, 4.0).forEach { hours ->
            val conc = result.concentration(hours)
            println("${hours}小时时浓度: $conc pg/mL")
        }
    }

    /**
     * 示例4：贴片应用和移除
     */
    fun example4_Patch() {
        println("\n=== 示例4：50µg/天贴片应用7天 ===")
        
        val events = listOf(
            DoseEvent(
                route = Route.PATCH_APPLY,
                timeH = 0.0,
                doseMG = 0.0,  // 零级释放不需要总剂量
                ester = Ester.E2,
                extras = mapOf(
                    DoseEvent.ExtraKey.RELEASE_RATE_UG_PER_DAY to 50.0
                )
            ),
            DoseEvent(
                route = Route.PATCH_REMOVE,
                timeH = 168.0,  // 7天后移除
                doseMG = 0.0,
                ester = Ester.E2
            )
        )
        
        val result = SimulationEngine.runSimulation(
            events = events,
            bodyWeightKG = 60.0
        )
        
        println("总AUC: ${result.auc} pg·h/mL")
        
        // 查询不同时间点的浓度
        listOf(24.0, 72.0, 168.0, 192.0).forEach { hours ->
            val conc = result.concentration(hours)
            val status = if (hours <= 168.0) "佩戴中" else "移除后"
            println("${hours}小时时浓度（$status）: $conc pg/mL")
        }
    }

    /**
     * 示例5：多次给药（每日2次口服）
     */
    fun example5_MultipleDoses() {
        println("\n=== 示例5：每日2次口服1mg雌二醇（连续3天） ===")
        
        val events = mutableListOf<DoseEvent>()
        
        // 3天，每天2次（早晚）
        for (day in 0 until 3) {
            // 早上8点
            events.add(
                DoseEvent(
                    route = Route.ORAL,
                    timeH = (day * 24.0) + 8.0,
                    doseMG = 1.0,
                    ester = Ester.E2
                )
            )
            // 晚上8点
            events.add(
                DoseEvent(
                    route = Route.ORAL,
                    timeH = (day * 24.0) + 20.0,
                    doseMG = 1.0,
                    ester = Ester.E2
                )
            )
        }
        
        val result = SimulationEngine.runSimulation(
            events = events,
            bodyWeightKG = 60.0
        )
        
        println("总AUC: ${result.auc} pg·h/mL")
        println("最大浓度: ${result.concPGmL.maxOrNull()} pg/mL")
        println("最小浓度（给药后）: ${result.concPGmL.drop(50).minOrNull()} pg/mL")
    }

    /**
     * 示例6：凝胶应用
     */
    fun example6_Gel() {
        println("\n=== 示例6：0.75mg凝胶涂抹750cm² ===")
        
        val event = DoseEvent(
            route = Route.GEL,
            timeH = 0.0,
            doseMG = 0.75,
            ester = Ester.E2,
            extras = mapOf(
                DoseEvent.ExtraKey.AREA_CM2 to 750.0
            )
        )
        
        val result = SimulationEngine.runSimulation(
            events = listOf(event),
            bodyWeightKG = 60.0
        )
        
        println("总AUC: ${result.auc} pg·h/mL")
        println("最大浓度: ${result.concPGmL.maxOrNull()} pg/mL")
        
        // 查询不同时间点的浓度
        listOf(12.0, 24.0, 36.0, 48.0).forEach { hours ->
            val conc = result.concentration(hours)
            println("${hours}小时时浓度: $conc pg/mL")
        }
    }

    /**
     * 运行所有示例
     */
    @JvmStatic
    fun main(args: Array<String>) {
        example1_SingleOralE2()
        example2_InjectionEV()
        example3_Sublingual()
        example4_Patch()
        example5_MultipleDoses()
        example6_Gel()
        
        println("\n=== 所有示例执行完成 ===")
    }
}
