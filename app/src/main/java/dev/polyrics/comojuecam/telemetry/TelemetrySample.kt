package dev.polyrics.comojuecam.telemetry

data class TelemetrySample(
    val timestampSeconds: Long,
    val gyroX: Double,
    val gyroY: Double,
    val gyroZ: Double,
    val accelX: Double,
    val accelY: Double,
    val accelZ: Double,
    val speedKmh: Double?,
    val latitude: Double?,
    val longitude: Double?
)
