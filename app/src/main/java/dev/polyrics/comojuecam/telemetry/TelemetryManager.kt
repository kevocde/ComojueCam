package dev.polyrics.comojuecam.telemetry

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import dev.polyrics.comojuecam.BuildConfig

class TelemetryManager(context: Context, private val settings: TelemetrySettings) {
    private val thread = HandlerThread("telemetry").apply { start() }
    private val handler = Handler(thread.looper)
    private val appContext = context.applicationContext
    private var collector: TelemetryCollector? = null
    private var sender: TelemetrySender? = null

    private val sendTick = object : Runnable {
        override fun run() {
            flush()
            handler.postDelayed(this, settings.sendIntervalMinutes * 60_000L)
        }
    }

    @Synchronized
    fun start() {
        if (collector != null) return
        val activeCollector = TelemetryCollector(appContext, settings, handler)
        collector = activeCollector
        sender = TelemetrySender(endpoint())
        activeCollector.start()
        handler.postDelayed(sendTick, settings.sendIntervalMinutes * 60_000L)
    }

    @Synchronized
    fun stop() {
        handler.removeCallbacks(sendTick)
        collector?.publishNow()
        flush()
        collector?.stop()
        sender?.close()
        collector = null
        sender = null
    }

    @Synchronized
    fun shutdown() {
        stop()
        thread.quitSafely()
    }

    private fun endpoint() =
        BuildConfig.TELEMETRY_URL.trimEnd('/') + "/ws/" + settings.token

    private fun flush() {
        val activeCollector = collector ?: return
        val activeSender = sender ?: return
        val batch = ArrayList<TelemetrySample>()
        var sample = activeCollector.samples.poll()
        while (sample != null) {
            batch.add(sample)
            sample = activeCollector.samples.poll()
        }
        if (batch.isEmpty()) return
        if (!activeSender.send(TelemetryJson.encode(batch))) {
            batch.forEach { activeCollector.samples.add(it) }
        }
    }
}
