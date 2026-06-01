package com.taoguo.post_saver.parser

import java.net.URI

/**
 * 分享链接与媒体 URL 规范化工具。
 */
object UrlNormalizer {

    private val HTTPS_PREFERRED_HOSTS = setOf(
        "xhslink.com",
        "xiaohongshu.com",
        "xhscdn.com",
        "ci.xiaohongshu.com",
        "douyin.com",
        "iesdouyin.com",
        "douyincdn.com",
        "douyinpic.com",
        "snssdk.com",
        "amemv.com",
        "byteimg.com",
        "ibyteimg.com",
        "toutiaoimg.com",
        "pstatp.com",
        "tiktokcdn.com",
        "douyinvod.com",
        "ixigua.com",
    )

    private const val DOUYIN_REFERER = "https://www.douyin.com/"
    private const val XHS_REFERER = "https://www.xiaohongshu.com/"

    /**
     * 规范化媒体或分享 URL（补全协议、升级 https、修正 HTML 实体）。
     *
     * @param url 输入：原始 URL。
     * @return 输出：可用于网络请求的 URL。
     */
    fun normalize(url: String): String {
        var result = url.trim()
        if (result.isEmpty()) {
            return result
        }
        if (result.startsWith("//")) {
            result = "https:$result"
        }
        result = result.replace("&amp;", "&")
        return ensureHttps(result)
    }

    /**
     * 将已知平台链接的 http 协议升级为 https，避免 Android 明文网络策略拦截。
     *
     * @param url 输入：原始 URL。
     * @return 输出：规范化后的 URL。
     */
    fun ensureHttps(url: String): String {
        if (!url.startsWith("http://", ignoreCase = true)) {
            return url
        }
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return url
        val shouldUpgrade = HTTPS_PREFERRED_HOSTS.any { host == it || host.endsWith(".$it") }
        if (!shouldUpgrade) {
            return url
        }
        return "https://${url.substring("http://".length)}"
    }

    /**
     * 根据媒体 URL 推断下载/预览所需的 Referer。
     *
     * @param url 输入：媒体 URL。
     * @return 输出：Referer 字符串。
     */
    fun refererFor(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("xhscdn.com") || lower.contains("xiaohongshu.com") -> XHS_REFERER
            else -> DOUYIN_REFERER
        }
    }
}
