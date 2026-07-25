package com.onebattery.app.battery

data class BatteryHealthSummary(
    val estimatedMah: Int?,
    val designMah: Int,
    val healthPercent: Int?,
    val sampleCount: Int,
)

suspend fun BatterySessionStore.healthSummary(designMah: Int): BatteryHealthSummary {
    val samples = recentEstimates(30)
    val avg = BatteryCapacityEstimator.averageMah(samples)
    return BatteryHealthSummary(
        estimatedMah = avg,
        designMah = designMah,
        healthPercent = avg?.let { BatteryCapacityEstimator.healthPercent(it, designMah) },
        sampleCount = samples.size,
    )
}
