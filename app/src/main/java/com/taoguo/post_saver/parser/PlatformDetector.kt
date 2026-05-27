package com.taoguo.post_saver.parser

import com.taoguo.post_saver.model.Platform
import java.net.URI

/**
 * 根据 URL 主机名识别内容平台。
 */
object PlatformDetector {

    private val DOUYIN_HOSTS = setOf(
        "douyin.com",
        "www.douyin.com",
        "v.douyin.com",
        "iesdouyin.com",
        "www.iesdouyin.com",
    )

    private val XHS_HOSTS = setOf(
        "xiaohongshu.com",
        "www.xiaohongshu.com",
        "xhslink.com",
    )

    /**
     * 识别 URL 所属平台。
     *
     * @param url 输入：待识别的链接。
     * @return 输出：平台枚举；无法识别时返回 UNKNOWN。
     */
    fun detect(url: String): Platform {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return Platform.UNKNOWN
        return when {
            DOUYIN_HOSTS.any { host == it || host.endsWith(".$it") } -> Platform.DOUYIN
            XHS_HOSTS.any { host == it || host.endsWith(".$it") } -> Platform.XIAOHONGSHU
            else -> Platform.UNKNOWN
        }
    }
}
