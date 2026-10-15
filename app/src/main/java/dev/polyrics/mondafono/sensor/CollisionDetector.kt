package dev.polyrics.mondafono.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
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
    private var previousAcceleration = 0f
    private var lastCollisionAt = 0L
    private var sensitivityGForce = 2.5f

    fun setSensitivity(gForce: Float) {
        sensitivityGForce = gForce
    }

    fun start() {
        if (active || accelerometer == null) return
        active = true
        previousAcceleration = 0f
        lastCollisionAt = 0L
        gravityInitialized = false
        gravity.fill(0f)
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!active) return
        sensorManager.unregisterListener(this)
        active = false
        previousAcceleration = 0f
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
        val threshold = sensitivityGForce * SensorManager.GRAVITY_EARTH
        val now = SystemClock.elapsedRealtime()

        if (previousAcceleration >= threshold && previousAcceleration >= acceleration &&
            now - lastCollisionAt >= MIN_COLLISION_INTERVAL_MS
        ) {
            lastCollisionAt = now
            onCollision()
        }
        previousAcceleration = acceleration
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val MIN_COLLISION_INTERVAL_MS = 500L
    }
}
