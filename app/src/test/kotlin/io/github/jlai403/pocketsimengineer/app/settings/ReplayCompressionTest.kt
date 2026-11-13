package io.github.jlai403.pocketsimengineer.app.settings

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayCompressionTest {
    @Test
    fun `original skips the transcode`() {
        val profile = replayProfile(
            preset = ReplayPreset.Original,
            keyframes = ReplayKeyframes.Fast,
            audio = ReplayAudio.Stereo,
            resolution = ReplayResolution.FullHd,
            codec = ReplayCodec.Hevc,
        )

        assertFalse(profile.transcode)
    }

    @Test
    fun `balanced is the full-quality default`() {
        val profile = replayProfile(
            preset = ReplayPreset.Balanced,
            keyframes = ReplayKeyframes.Fast,
            audio = ReplayAudio.Stereo,
            resolution = ReplayResolution.FullHd,
            codec = ReplayCodec.Hevc,
        )

        assertTrue(profile.transcode)
        assertEquals(1920, profile.width)
        assertEquals(1080, profile.height)
        assertEquals(5_000_000, profile.videoBitrate)
        assertEquals(0.5f, profile.iFrameIntervalSeconds)
        assertEquals(MimeTypes.VIDEO_H265, profile.videoMime)
        assertEquals(96_000, profile.audioBitrate)
    }

    @Test
    fun `compact lowers the bitrate`() {
        val profile = replayProfile(
            preset = ReplayPreset.Compact,
            keyframes = ReplayKeyframes.Sparse,
            audio = ReplayAudio.Mono,
            resolution = ReplayResolution.Hd,
            codec = ReplayCodec.H264,
        )

        assertEquals(1280, profile.width)
        assertEquals(720, profile.height)
        assertEquals(3_000_000, profile.videoBitrate)
        assertEquals(2f, profile.iFrameIntervalSeconds)
        assertEquals(MimeTypes.VIDEO_H264, profile.videoMime)
        assertEquals(64_000, profile.audioBitrate)
    }

    @Test
    fun `captions explain the selection`() {
        assertTrue(presetCaption(ReplayPreset.Compact).contains("75% smaller"))
        assertTrue(keyframesCaption(ReplayKeyframes.Fast).contains("scrub"))
        assertTrue(audioCaption(ReplayAudio.Remove).contains("no audio"))
        assertTrue(resolutionCaption(ReplayResolution.Hd).contains("25% smaller"))
        assertTrue(codecCaption(ReplayCodec.H264).contains("older devices"))
    }
}
