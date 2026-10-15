package dev.polyrics.mondafono.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class CollisionDetector(
    context: Context,
    private val onCollision: () -> Unit,
    private val onGForceChanged: (Float) -> Unit
) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gravity = FloatArray(3)
    private var active = false
    private var gravityInitialized = false
    private var thresholdReached = false

    fun start() {
        if (active || accelerometer == null) return
        active = true
        thresholdReached = false
        gravityInitialized = false
        gravity.fill(0f)
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!active) return
        sensorManager.unregisterListener(this)
        active = false
        thresholdReached = false
        gravityInitialized = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!gravityInitialized) {
            event.values.copyInto(gravity)
            gravityInitialized = true
            return
        }
        val alpha = 0.8f
        for (axis in 0..2) {
            gravity[axis] = alpha * gravity[axis] + (1f - alpha) * event.values[axis]
        }

        val linearX = event.values[0] - gravity[0]
        val linearY = event.values[1] - gravity[1]
        val linearZ = event.values[2] - gravity[2]
        val acceleration = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)
        onGForceChanged(acceleration / SensorManager.GRAVITY_EARTH)
        val threshold = 2.5f * SensorManager.GRAVITY_EARTH

        if (acceleration >= threshold) {
            if (!thresholdReached) {
                onCollision()
            }
            thresholdReached = true
        } else if (acceleration < threshold * RESET_RATIO) {
            thresholdReached = false
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val RESET_RATIO = 0.8f
    }
}
