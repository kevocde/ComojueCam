package dev.polyrics.comojuecam.recording

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import java.io.File
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

data class EncodedSample(
    val data: ByteArray,
    val presentationTimeUs: Long,
    val flags: Int
)

class LoopRecording(
    private val format: MediaFormat,
    samples: Collection<EncodedSample>,
    val durationSeconds: Int,
    private val filePrefix: String = ""
) {
    val fileName = filePrefix + SimpleDateFormat("yyMMdd-HHmmss", Locale.US).format(Date()) + ".mp4"
    private val samples = samples.toList().let { allSamples ->
        val firstKeyFrame = allSamples.indexOfFirst {
            it.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
        }
        if (firstKeyFrame >= 0) allSamples.drop(firstKeyFrame) else allSamples
    }.toMutableList()

    fun saveTo(file: File) {
        file.parentFile?.mkdirs()
        saveToMuxer(MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4))
    }

    fun saveTo(fileDescriptor: FileDescriptor) {
        saveToMuxer(MediaMuxer(fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4))
    }

    fun release() {
        samples.clear()
    }

    private fun saveToMuxer(muxer: MediaMuxer) {
        try {
            val track = muxer.addTrack(format)
            muxer.start()
            val startTimeUs = samples.firstOrNull()?.presentationTimeUs ?: 0L
            samples.forEach { sample ->
                val info = MediaCodec.BufferInfo().apply {
                    set(0, sample.data.size, sample.presentationTimeUs - startTimeUs, sample.flags)
                }
                muxer.writeSampleData(track, ByteBuffer.wrap(sample.data), info)
            }
            muxer.stop()
        } finally {
            muxer.release()
            release()
        }
    }
}

data class RecordingStopResult(
    val manualRecording: LoopRecording?,
    val incidentRecordings: List<LoopRecording>
)

class LoopVideoRecorder(
    private val width: Int,
    private val height: Int,
    private val frameRate: Int,
    private val durationSeconds: Int
) {
    private val codec = MediaCodec.createEncoderByType("video/avc")
    private val format = MediaFormat.createVideoFormat("video/avc", width, height).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
        setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    }
    private val maxDurationUs = durationSeconds * 1_000_000L
    private val samples = ArrayDeque<EncodedSample>()
    private val bufferLock = Any()
    private val incidentCaptures = mutableListOf<IncidentCapture>()
    val inputSurface: Surface
    private var outputFormat: MediaFormat? = null
    private var thread: Thread? = null

    init {
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = codec.createInputSurface()
    }

    fun start() {
        codec.start()
        var lastPresentationTimeUs = 0L
        val backoff = AdaptiveBackoff()
        thread = Thread {
            val info = MediaCodec.BufferInfo()
            while (true) {
                val index = codec.dequeueOutputBuffer(info, backoff.nextTimeout())
                when (index) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = codec.outputFormat
                    MediaCodec.INFO_TRY_AGAIN_LATER -> backoff.onRetry()
                    else -> if (index >= 0) {
                        backoff.onSuccess()
                        val buffer = codec.getOutputBuffer(index)
                        if (info.size > 0 && buffer != null &&
                            info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                        ) {
                            buffer.position(info.offset)
                            val data = ByteArray(info.size)
                            buffer.get(data)
                            lastPresentationTimeUs = info.presentationTimeUs
                            appendSample(EncodedSample(data, info.presentationTimeUs, info.flags))
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        }.also { it.start() }
    }

    fun startIncidentCapture(
        preDurationSeconds: Int,
        postDurationSeconds: Int,
        onComplete: (LoopRecording) -> Unit
    ): Boolean {
        synchronized(bufferLock) {
            val latestTimeUs = samples.lastOrNull()?.presentationTimeUs ?: return false
            val cutoff = latestTimeUs - preDurationSeconds * 1_000_000L
            val previousSamples = samples.filter { it.presentationTimeUs >= cutoff }
            incidentCaptures += IncidentCapture(
                previousSamples.toMutableList(),
                latestTimeUs + postDurationSeconds * 1_000_000L,
                preDurationSeconds + postDurationSeconds,
                onComplete
            )
        }
        return true
    }

    fun extendIncidentCapture(extraSeconds: Int) {
        synchronized(bufferLock) {
            incidentCaptures.forEach { it.extend(extraSeconds * 1_000_000L) }
        }
    }

    fun stop(): RecordingStopResult {
        var result: RecordingStopResult? = null
        try {
            codec.signalEndOfInputStream()
            thread?.join()
            outputFormat?.let {
                val bufferedSamples = synchronized(bufferLock) { samples.toList() }
                val incidents = synchronized(bufferLock) {
                    incidentCaptures.map { capture -> capture.toRecording(it) }
                }
                result = RecordingStopResult(
                    LoopRecording(it, bufferedSamples, durationSeconds),
                    incidents
                )
            }
        } finally {
            inputSurface.release()
            codec.stop()
            codec.release()
        }
        return result ?: RecordingStopResult(null, emptyList())
    }

    private fun trimSamples(latestPresentationTimeUs: Long) {
        val cutoff = latestPresentationTimeUs - maxDurationUs
        while (samples.size > 1 && samples.first().presentationTimeUs < cutoff) {
            samples.removeFirst()
        }
    }

    private fun appendSample(sample: EncodedSample) {
        val completedCaptures: List<IncidentCapture>
        synchronized(bufferLock) {
            samples.addLast(sample)
            trimSamples(sample.presentationTimeUs)
            completedCaptures = incidentCaptures.filter { it.append(sample) }
            incidentCaptures.removeAll(completedCaptures.toSet())
        }
        completedCaptures.forEach { capture ->
            outputFormat?.let {
                capture.onComplete(
                    LoopRecording(it, capture.samples, capture.durationSeconds, "incient_")
                )
            }
        }
    }

    private class IncidentCapture(
        val samples: MutableList<EncodedSample>,
        private var endTimeUs: Long,
        val durationSeconds: Int,
        val onComplete: (LoopRecording) -> Unit
    ) {
        fun append(sample: EncodedSample): Boolean {
            samples += sample
            return sample.presentationTimeUs >= endTimeUs
        }

        fun extend(extraTimeUs: Long) {
            endTimeUs += extraTimeUs
        }

        fun toRecording(format: MediaFormat): LoopRecording {
            return LoopRecording(format, samples, durationSeconds, "incient_")
        }
    }
}
