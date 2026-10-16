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
        sample.speedKmh?.let { put("velocity", JSONObject().put("speed", round(it, 1))) }
        put("gps", JSONObject().apply {
            sample.latitude?.let { put("latitude", round(it, 5)) }
            sample.longitude?.let { put("longitude", round(it, 5)) }
        })
    }

    private fun vector(x: Double, y: Double, z: Double) = JSONObject()
        .put("x", round(x, 3))
        .put("y", round(y, 3))
        .put("z", round(z, 3))

    private fun round(value: Double, digits: Int): Double {
        val factor = Math.pow(10.0, digits.toDouble())
        return Math.round(value * factor) / factor
    }
}
