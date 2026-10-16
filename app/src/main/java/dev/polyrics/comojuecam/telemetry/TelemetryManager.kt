package dev.polyrics.comojuecam.telemetry

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.HandlerThread
import dev.polyrics.comojuecam.BuildConfig

class TelemetryManager(context: Context, private val settings: TelemetrySettings) {
    private val thread = HandlerThread("telemetry").apply { start() }
    private val handler = Handler(thread.looper)
    private val appContext = context.applicationContext
    private val outbox = TelemetryOutbox(appContext)
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val sender = TelemetrySender(endpoint())
    @Volatile private var collector: TelemetryCollector? = null
    private var retryCount = 0

    private val sendTick = object : Runnable {
        override fun run() {
            collector?.let { drainSamples(it) }
            restartDrain()
            handler.postDelayed(this, settings.sendIntervalMinutes * 60_000L)
        }
    }

    private val retryTick = Runnable { flushOutbox() }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.post(::restartDrain)
        }
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    @Synchronized
    fun start() {
        if (collector != null) return
        val activeCollector = TelemetryCollector(appContext, settings, handler)
        collector = activeCollector
        activeCollector.start()
        handler.post(::restartDrain)
        handler.postDelayed(sendTick, settings.sendIntervalMinutes * 60_000L)
    }

    @Synchronized
    fun stop() {
        handler.removeCallbacks(sendTick)
        val activeCollector = collector ?: return
        collector = null
        handler.post {
            activeCollector.publishNow()
            drainSamples(activeCollector)
            activeCollector.stop()
            restartDrain()
        }
    }

    @Synchronized
    fun shutdown() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
        stop()
        thread.quitSafely()
    }

    private fun endpoint() =
        BuildConfig.TELEMETRY_URL.trimEnd('/') + "/ws/" + settings.token

    private fun drainSamples(activeCollector: TelemetryCollector) {
        val batch = ArrayList<TelemetrySample>()
        var sample = activeCollector.samples.poll()
        while (sample != null) {
            batch.add(sample)
            sample = activeCollector.samples.poll()
        }
        if (batch.isNotEmpty()) outbox.save(TelemetryJson.encode(batch))
    }

    private fun restartDrain() {
        handler.removeCallbacks(retryTick)
        retryCount = 0
        if (outbox.hasPending()) flushOutbox()
    }

    private fun flushOutbox() {
        if (sendAllPending()) {
            retryCount = 0
            return
        }
        if (retryCount >= MAX_RETRIES) return
        retryCount++
        handler.postDelayed(retryTick, RETRY_BASE_MS shl (retryCount - 1))
    }

    private fun sendAllPending(): Boolean {
        var allSent = true
        outbox.readPending().forEach { packet ->
            if (allSent && sender.send(packet.content)) outbox.delete(packet) else allSent = false
        }
        return allSent
    }

    private companion object {
        const val MAX_RETRIES = 5
        const val RETRY_BASE_MS = 30_000L
    }
}
