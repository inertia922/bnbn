package blbl.cat3399.feature.player

import androidx.media3.common.PlaybackException
import blbl.cat3399.core.api.BiliApiException

private const val NETWORK_HINT = "请检查网络或稍后重试"
private const val CODEC_HINT = "请切换清晰度/视频编码，或改用 ExoPlayer 内核"

/**
 * Human readable reason for a playback failure.
 *
 * Keep this free of raw exception class names where possible: the player error UI is the
 * last place an ordinary viewer can understand what went wrong.
 */
internal fun playbackFailureTip(
    error: Throwable,
    httpStatusCode: Int? = null,
): String {
    val apiError = error as? BiliApiException
    if (apiError != null) return playbackFailureTipForApi(apiError.apiCode, apiError.apiMessage)

    val playbackException = error as? PlaybackException
    if (playbackException != null) {
        return playbackFailureTipForExo(
            errorCode = playbackException.errorCode,
            httpStatusCode = httpStatusCode,
        )
    }

    val message = error.message.orEmpty().trim()
    // IjkPlayer reports raw FFmpeg/MediaCodec errors. Map the common codes to actionable text.
    when {
        message.contains("-1004") -> return "视频读取失败（网络中断或地址失效），$NETWORK_HINT"
        message.contains("-1007") -> return "视频数据格式异常，$CODEC_HINT"
        message.contains("-1010") -> return "当前设备不支持该视频编码，$CODEC_HINT"
        message.contains("-110") -> return "视频加载超时，$NETWORK_HINT"
    }
    return when {
        message.contains("No playable url", ignoreCase = true) ->
            "没有获取到可播放的视频流，可能是会员/地区限制或清晰度不可用"
        message.contains("view detail missing", ignoreCase = true) ->
            "视频信息加载失败，请检查网络后重试"
        message.contains("cid missing", ignoreCase = true) ->
            "缺少播放参数（cid），请重新打开视频"
        message.contains("bvid or aid required", ignoreCase = true) ->
            "缺少播放参数（bvid/aid），请重新打开视频"
        message.contains("风控", ignoreCase = true) ->
            "请求被风控拦截，请完成验证后重试"
        message.isNotBlank() -> "播放失败：$message"
        else -> "播放失败，请重试或切换播放器内核"
    }
}

internal fun playbackFailureTipForExo(
    errorCode: Int,
    httpStatusCode: Int? = null,
): String {
    return when (errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> "网络连接失败，$NETWORK_HINT"

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
            val status = httpStatusCode
            when {
                status == null -> "视频请求失败（HTTP 状态异常），$NETWORK_HINT"
                status == 401 || status == 403 -> "播放地址已失效或被拒绝（HTTP 403），请重新打开视频"
                status == 404 || status == 410 -> "视频资源不存在或已下架（HTTP 404）"
                status == 416 -> "视频分段请求异常（HTTP 416），请重新打开视频"
                status in 500..599 -> "视频服务器暂时不可用（HTTP $status），请稍后重试"
                else -> "视频请求失败（HTTP $status），$NETWORK_HINT"
            }
        }

        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "视频资源不存在，请重新打开视频"

        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
        -> "读取视频数据失败，$NETWORK_HINT"

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        -> "视频流格式异常，$CODEC_HINT"

        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        -> "当前设备无法解码该视频，$CODEC_HINT"

        PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
        PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED,
        PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
        -> "该视频受版权保护，当前设备无法播放"

        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> "直播画面已落后，请重新进入直播间"

        PlaybackException.ERROR_CODE_TIMEOUT -> "播放超时，$NETWORK_HINT"

        else -> "播放失败，请重试或切换播放器内核"
    }
}

internal fun playbackFailureTipForApi(
    apiCode: Int,
    apiMessage: String?,
): String {
    val message = apiMessage.orEmpty().trim()
    return when {
        apiCode == -404 -> "当前视频暂时无法播放（可能已下架或地区限制）"
        apiCode == -403 -> "没有播放权限（可能受地区/版权限制）"
        apiCode == -10403 -> "该视频受地区限制，当前网络无法播放"
        apiCode == 87007 || apiCode == 87008 -> "该视频需要大会员或付费购买后才能播放"
        apiCode == -412 || apiCode == -352 || message.contains("风控") ->
            "请求被风控拦截，请完成验证后重试"
        message.isNotBlank() -> "加载播放信息失败：$message"
        else -> "加载播放信息失败，请稍后重试"
    }
}
