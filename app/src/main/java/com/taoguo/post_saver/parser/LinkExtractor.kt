package com.taoguo.post_saver.parser

/**
 * 从分享文本中提取 HTTP/HTTPS 链接。
 */
object LinkExtractor {

    private val URL_PATTERN = Regex("""https?://[^\s<>\"']+""")

    /**
     * 提取文本中所有 URL，并去除末尾常见标点。
     *
     * @param text 输入：用户粘贴的分享文本。
     * @return 输出：去重后的 URL 列表，按出现顺序排列。
     */
    fun extractUrls(text: String): List<String> {
        return URL_PATTERN.findAll(text)
            .map { match ->
                match.value.trimEnd(',', '.', ';', '，', '。', '！', '!', ')', '）', ']')
            }
            .distinct()
            .toList()
    }

    /**
     * 提取第一个可被识别的平台链接。
     *
     * @param text 输入：用户粘贴的分享文本。
     * @return 输出：第一个平台 URL；若无则返回 null。
     */
    fun extractFirstPlatformUrl(text: String): String? {
        return extractUrls(text).firstOrNull { url ->
            PlatformDetector.detect(url) != com.taoguo.post_saver.model.Platform.UNKNOWN
        }
    }
}
