package blbl.cat3399.feature.player

import androidx.media3.common.PlaybackException
import blbl.cat3399.core.api.BiliApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPlaybackFailureTipsTest {
    @Test
    fun httpFailureExplainsTheActualStatus() {
        assertTrue(
            playbackFailureTipForExo(
                errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                httpStatusCode = 403,
            ).contains("403"),
        )
        assertTrue(
            playbackFailureTipForExo(
                errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                httpStatusCode = 404,
            ).contains("不存在"),
        )
        assertTrue(
            playbackFailureTipForExo(
                errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                httpStatusCode = 502,
            ).contains("502"),
        )
    }

    @Test
    fun decoderFailureSuggestsAnAction() {
        val tip = playbackFailureTipForExo(PlaybackException.ERROR_CODE_DECODING_FAILED)
        assertTrue(tip.contains("解码"))
        assertTrue(tip.contains("切换"))
    }

    @Test
    fun networkFailureIsRecognised() {
        assertTrue(
            playbackFailureTipForExo(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
                .contains("网络"),
        )
    }

    @Test
    fun apiCodesAreMappedToUserFacingReasons() {
        assertEquals(
            "当前视频暂时无法播放（可能已下架或地区限制）",
            playbackFailureTipForApi(apiCode = -404, apiMessage = "啥都别说了"),
        )
        assertEquals(
            "该视频需要大会员或付费购买后才能播放",
            playbackFailureTipForApi(apiCode = 87007, apiMessage = ""),
        )
        assertTrue(
            playbackFailureTipForApi(apiCode = -10403, apiMessage = "")
                .contains("地区限制"),
        )
    }

    @Test
    fun genericEngineErrorsKeepActionableHints() {
        assertTrue(playbackFailureTip(Exception("IjkMediaPlayer error what=-1004 extra=0")).contains("网络"))
        assertTrue(playbackFailureTip(IllegalStateException("No playable url in playurl response")).contains("可播放"))
        assertTrue(playbackFailureTip(BiliApiException(apiCode = -412, apiMessage = "风控")).contains("风控"))
    }
}
