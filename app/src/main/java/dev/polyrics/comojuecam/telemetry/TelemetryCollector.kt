package dev.polyrics.comojuecam.telemetry

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import java.util.concurrent.ConcurrentLinkedQueue

class TelemetryCollector(
    context: Context,
    private val settings: TelemetrySettings,
    private val handler: Handler
) : SensorEventListener, LocationListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val gyroAverage = SensorAverage()
    private val accelAverage = SensorAverage()
    private var lastLocation: Location? = null
    val samples = ConcurrentLinkedQueue<TelemetrySample>()

    private val tick = object : Runnable {
        override fun run() {
            publishSample()
            handler.postDelayed(this, settings.sampleSeconds * 1000L)
        }
    }

    fun start() {
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, handler)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, handler)
        }
        requestLocationUpdates()
        handler.postDelayed(tick, settings.sampleSeconds * 1000L)
    }

    fun stop() {
        handler.removeCallbacks(tick)
        sensorManager.unregisterListener(this)
        locationManager.removeUpdates(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> gyroAverage.add(event.values)
            Sensor.TYPE_ACCELEROMETER -> accelAverage.add(event.values)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    override fun onLocationChanged(location: Location) {
        lastLocation = location
    }

    private fun requestLocationUpdates() {
        try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .filter { locationManager.allProviders.contains(it) }
            providers.forEach {
                locationManager.requestLocationUpdates(
                    it, settings.sampleSeconds * 1000L, 0f, this, handler.looper
                )
            }
        } catch (_: SecurityException) {
        }
    }

    private fun publishSample() {
        val gyro = gyroAverage.averageAndReset()
        val accel = accelAverage.averageAndReset()
        val location = lastLocation
        samples.add(
            TelemetrySample(
                timestampSeconds = System.currentTimeMillis() / 1000L,
                gyroX = gyro[0], gyroY = gyro[1], gyroZ = gyro[2],
                accelX = accel[0], accelY = accel[1], accelZ = accel[2],
                speedKmh = location?.takeIf { it.hasSpeed() }?.let { it.speed * 3.6 },
                latitude = location?.latitude,
                longitude = location?.longitude
            )
        )
    }

    private class SensorAverage {
        private val sum = DoubleArray(3)
        private val last = DoubleArray(3)
        private var count = 0

        fun add(values: FloatArray) {
            for (index in 0..2) sum[index] += values[index].toDouble()
            count++
        }

        fun averageAndReset(): DoubleArray {
            if (count > 0) {
                for (index in 0..2) last[index] = sum[index] / count
                sum.fill(0.0)
                count = 0
            }
            return last.copyOf()
        }
    }
}
