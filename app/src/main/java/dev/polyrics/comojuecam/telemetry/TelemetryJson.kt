package dev.polyrics.comojuecam.telemetry

import org.json.JSONObject

object TelemetryJson {
    fun encode(samples: List<TelemetrySample>): String {
        val root = JSONObject()
        samples.forEach { root.put(it.timestampSeconds.toString(), group(it)) }
        return root.toString()
    }

    private fun group(sample: TelemetrySample) = JSONObject().apply {
        put("gyroscope", vector(sample.gyroX, sample.gyroY, sample.gyroZ))
        put("accelerometer", vector(sample.accelX, sample.accelY, sample.accelZ))
        sample.speedKmh?.let { put("velocity", JSONObject().put("speed", round(it))) }
        put("gps", JSONObject().apply {
            sample.latitude?.let { put("latitude", round(it)) }
            sample.longitude?.let { put("longitude", round(it)) }
        })
    }

    private fun vector(x: Double, y: Double, z: Double) = JSONObject()
        .put("x", round(x))
        .put("y", round(y))
        .put("z", round(z))

    private fun round(value: Double) = Math.round(value * 100) / 100.0
}
