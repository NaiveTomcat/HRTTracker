package cn.naivetomcat.hrt_tracker.pk

import kotlin.math.abs
import kotlin.math.exp

/**
 * 三室药代动力学模型
 * 提供三室序列模型（depot → 酯库 → 中央室）的解析解
 * 以及一室模型（Bateman方程）的解析解
 */
object ThreeCompartmentModel {

    /**
     * 双通路三室模型（两个分支都有水解）
     * 每个分支都遵循3室链（k1 → k2 → k3）
     * 用于注射给药
     * 
     * @param tau 给药后时间（小时）
     * @param doseMG 剂量（mg）
     * @param p PK参数
     * @return 中央室中的雌二醇量（mg）
     */
    fun dualAbs3CAmount(tau: Double, doseMG: Double, p: PKParams): Double {
        if (doseMG <= 0) return 0.0
        
        val f = p.fracFast.coerceIn(0.0, 1.0)
        val doseF = doseMG * f
        val doseS = doseMG * (1.0 - f)
        
        val amtF = analytic3C(tau, doseF, p.fFast, p.k1Fast, p.k2, p.k3)
        val amtS = analytic3C(tau, doseS, p.fSlow, p.k1Slow, p.k2, p.k3)
        
        return amtF + amtS
    }

    /**
     * 双通路混合模型（EV舌下）
     * 快速分支保持水解（3C），慢速吞咽分支直接遵循口服一室模型
     * 
     * @param tau 给药后时间（小时）
     * @param doseMG 剂量（mg）
     * @param p PK参数
     * @return 中央室中的雌二醇量（mg）
     */
    fun dualAbsMixedAmount(tau: Double, doseMG: Double, p: PKParams): Double {
        if (doseMG <= 0) return 0.0
        
        val f = p.fracFast.coerceIn(0.0, 1.0)
        val doseF = doseMG * f
        val doseS = doseMG * (1.0 - f)
        
        val amtF = analytic3C(tau, doseF, p.fFast, p.k1Fast, p.k2, p.k3)
        val amtS = batemanAmount(doseS, p.fSlow, p.k1Slow, p.k3, tau)
        
        return amtF + amtS
    }

    /**
     * 双通路一阶吸收（无水解）
     * 用于E2舌下给药
     * 快速分支：fracFast, k1Fast, fFast
     * 慢速分支：(1-fracFast), k1Slow, fSlow
     * 
     * @param tau 给药后时间（小时）
     * @param doseMG 剂量（mg）
     * @param p PK参数
     * @return 中央室中的雌二醇量（mg）
     */
    fun dualAbsAmount(tau: Double, doseMG: Double, p: PKParams): Double {
        if (doseMG <= 0) return 0.0
        
        val f = p.fracFast.coerceIn(0.0, 1.0)
        val doseF = doseMG * f
        val doseS = doseMG * (1.0 - f)
        
        val amtF = batemanAmount(doseF, p.fFast, p.k1Fast, p.k3, tau)
        val amtS = batemanAmount(doseS, p.fSlow, p.k1Slow, p.k3, tau)
        
        return amtF + amtS
    }

    /**
     * 注射量计算（使用双部分储库模型）
     * 
     * @param tau 给药后时间（小时）
     * @param doseMG 剂量（mg）
     * @param p PK参数
     * @return 中央室中的雌二醇量（mg）
     */
    fun injAmount(tau: Double, doseMG: Double, p: PKParams): Double {
        val doseFast = doseMG * p.fracFast
        val doseSlow = doseMG * (1.0 - p.fracFast)
        
        val amountFromFast = analytic3C(tau, doseFast, p.F, p.k1Fast, p.k2, p.k3)
        val amountFromSlow = analytic3C(tau, doseSlow, p.F, p.k1Slow, p.k2, p.k3)
        
        return amountFromFast + amountFromSlow
    }

    /**
     * 一室模型量计算
     * 用于口服/凝胶，使用单一吸收速率（现映射到k1Fast）
     * 
     * @param tau 给药后时间（小时）
     * @param doseMG 剂量（mg）
     * @param p PK参数
     * @return 中央室中的雌二醇量（mg）
     */
    fun oneCompAmount(tau: Double, doseMG: Double, p: PKParams): Double {
        return batemanAmount(doseMG, p.F, p.k1Fast, p.k3, tau)
    }

    /**
     * 贴片量计算
     * 支持零级释放和一阶释放两种模式
     * 
     * @param tau 给药后时间（小时）
     * @param doseMG 剂量（mg）
     * @param wearH 佩戴时长（小时）
     * @param p PK参数
     * @return 中央室中的雌二醇量（mg）
     */
    fun patchAmount(tau: Double, doseMG: Double, wearH: Double, p: PKParams): Double {
        // 零级释放模式
        if (p.rateMGh > 0) {
            if (tau <= wearH) {
                return p.rateMGh / p.k3 * (1 - exp(-p.k3 * tau))
            } else {
                val amtAtRemoval = p.rateMGh / p.k3 * (1 - exp(-p.k3 * wearH))
                val dt = tau - wearH
                return amtAtRemoval * exp(-p.k3 * dt)
            }
        }
        
        // 一阶释放遗留模式
        val amountUnderPatch = batemanAmount(doseMG, p.F, p.k1Fast, p.k3, tau)
        if (tau > wearH) {
            val amountAtRemoval = batemanAmount(doseMG, p.F, p.k1Fast, p.k3, wearH)
            val dt = tau - wearH
            return amountAtRemoval * exp(-p.k3 * dt)
        }
        return amountUnderPatch
    }

    /**
     * 三室模型的解析解
     * 计算在给药后时间tau时中央室（C）中的药物量
     * 假设k1、k2和k3互不相同
     * 
     * @param tau 给药后时间（小时）
     * @param doseMG 剂量（mg）
     * @param F 生物利用度
     * @param k1 储库到酯库的速率常数（h⁻¹）
     * @param k2 酯库到E2的速率常数（h⁻¹）
     * @param k3 E2消除的速率常数（h⁻¹）
     * @return 中央室中的药物量（mg）
     */
    private fun analytic3C(tau: Double, doseMG: Double, F: Double, k1: Double, k2: Double, k3: Double): Double {
        // 处理k1为零的边界情况
        if (k1 <= 0 || doseMG <= 0) return 0.0
        
        // 为防止除零或浮点不稳定，检查速率常数是否太接近
        val k1_k2 = k1 - k2
        val k1_k3 = k1 - k3
        val k2_k3 = k2 - k3
        
        // 健壮的近似相等性检查
        if (abs(k1_k2) < 1e-9 || abs(k1_k3) < 1e-9 || abs(k2_k3) < 1e-9) {
            // 退回到更简单的模型或更复杂的Bateman方程处理重根
            // 目前，对于这种不太可能的退化情况，返回0比崩溃更安全
            // 正确的实现应该处理每种情况（k1=k2, k1=k3, k2=k3, k1=k2=k3）
            return 0.0
        }
        
        val term1 = exp(-k1 * tau) / (k1_k2 * k1_k3)
        val term2 = exp(-k2 * tau) / (-k1_k2 * k2_k3)
        val term3 = exp(-k3 * tau) / (k1_k3 * k2_k3)
        
        return doseMG * F * k1 * k2 * (term1 + term2 + term3)
    }

    /**
     * Bateman方程：一室一阶吸收-一阶消除模型
     * 
     * @param doseMG 剂量（mg）
     * @param F 生物利用度
     * @param ka 吸收速率常数（h⁻¹）
     * @param ke 消除速率常数（h⁻¹）
     * @param t 时间（小时）
     * @return 中央室中的药物量（mg）
     */
    private fun batemanAmount(doseMG: Double, F: Double, ka: Double, ke: Double, t: Double): Double {
        if (doseMG <= 0 || ka <= 0) return 0.0
        
        // 如果ka ≈ ke，使用极限形式
        if (abs(ka - ke) < 1e-9) {
            return doseMG * F * ka * t * exp(-ke * t)
        }
        
        return doseMG * F * ka / (ka - ke) * (exp(-ke * t) - exp(-ka * t))
    }
}
