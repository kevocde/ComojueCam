package dev.polyrics.mondafono.recording

data class VideoSize(val width: Int, val height: Int) {
    override fun toString() = "${width}x${height}"
}

enum class CollisionSensitivity(val gForce: Float) {
    SOFT(2.5f),
    MEDIUM(5f),
    HARD(8f)
}

class RecordingSettings {
    var supportedFrameRates = listOf(30)
    var frameRate = 30
    var supportedSizes = emptyList<VideoSize>()
    var size = VideoSize(1280, 720)
    var loopDurationSeconds = 30
    var incidentPostDurationSeconds = 30
    var extraCollisionTimeSeconds = 20
    var collisionSensitivity = CollisionSensitivity.SOFT
}
