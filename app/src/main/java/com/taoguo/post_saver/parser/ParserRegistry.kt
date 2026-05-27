package com.taoguo.post_saver.parser

import com.taoguo.post_saver.model.ParseResult
import com.taoguo.post_saver.model.Platform

/**
 * 平台解析器注册表，统一调度各平台解析逻辑。
 */
object ParserRegistry {

    private val douyinParser = DouyinParser()
    private val xiaohongshuParser = XiaohongshuParser()

    /**
     * 解析分享文本，自动识别平台并调用对应解析器。
     *
     * @param text 输入：用户粘贴的分享文本。
     * @return 输出：解析结果。
     * @throws ParseException 无有效链接或解析失败时抛出。
     * @throws UnsupportedPlatformException 平台暂不支持时抛出。
     */
    fun parseText(text: String): ParseResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            throw ParseException("请先粘贴分享链接或文本")
        }

        val url = LinkExtractor.extractFirstPlatformUrl(trimmed)
            ?: LinkExtractor.extractUrls(trimmed).firstOrNull()?.let { firstUrl ->
                throw ParseException("暂不支持该平台链接：$firstUrl")
            }
            ?: throw ParseException("未在文本中找到有效链接")

        return parseUrl(url)
    }

    /**
     * 解析单个平台 URL。
     *
     * @param url 输入：平台分享链接。
     * @return 输出：解析结果。
     * @throws ParseException 解析失败时抛出。
     * @throws UnsupportedPlatformException 平台暂不支持时抛出。
     */
    fun parseUrl(url: String): ParseResult {
        return when (PlatformDetector.detect(url)) {
            Platform.DOUYIN -> douyinParser.parse(url)
            Platform.XIAOHONGSHU -> xiaohongshuParser.parse(url)
            Platform.UNKNOWN -> throw ParseException("无法识别链接所属平台")
        }
    }
}
