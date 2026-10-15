package dev.polyrics.mondafono.recording

data class VideoSize(val width: Int, val height: Int) {
    override fun toString() = "${width}x${height}"
}

class RecordingSettings {
    var supportedFrameRates = listOf(30)
    var frameRate = 30
    var supportedSizes = emptyList<VideoSize>()
    var size = VideoSize(1280, 720)
    var loopDurationSeconds = 30
}
