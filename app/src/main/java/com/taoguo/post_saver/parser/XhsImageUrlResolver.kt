package com.taoguo.post_saver.parser

/**
 * 小红书图片 URL 解析：预览转高清、生成可下载候选链。
 */
object XhsImageUrlResolver {

    private val SPECTRUM_KEY_PATTERN = Regex("""/spectrum/([^!/]+)""", RegexOption.IGNORE_CASE)
    private val FILE_ID_PATTERN = Regex(
        """(?:notes_pre_post|note_pre_post_uhdr|spectrum)/([^!?/]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val CI_PATH_PATTERN = Regex(
        """xhscdn\.com/\d+/[a-f0-9]+/(.+?)(?:!|$)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 从 xhscdn 图片 URL 提取 spectrum 文件 ID。
     *
     * @param url 输入：图片 URL。
     * @return 输出：文件 ID；无法提取时返回 null。
     */
    fun spectrumKey(url: String): String? {
        return SPECTRUM_KEY_PATTERN.find(url)?.groupValues?.getOrNull(1)
    }

    /**
     * 提取图片在笔记中的唯一标识（fileId / spectrum），用于合并与去重。
     *
     * @param url 输入：图片 URL。
     * @return 输出：唯一键；无法提取时返回 null。
     */
    fun imageIdentityKey(url: String): String? {
        FILE_ID_PATTERN.find(url)?.let { match ->
            return "${match.groupValues[0]}"
        }
        return spectrumKey(url)
    }

    /**
     * 将预览图 URL 尽量升级为高清下载地址。
     *
     * @param url 输入：原始图片 URL。
     * @return 输出：更适合下载的 URL。
     */
    fun upgradePreviewToHd(url: String): String {
        return url
            .replace("!nc_n_webp_prv_1", "!nc_n_webp_mw_1", ignoreCase = true)
            .replace("!nc_n_webp_prv", "!nc_n_webp_mw", ignoreCase = true)
            .replace("webp_prv", "webp_mw", ignoreCase = true)
    }

    /**
     * 构建 ci.xiaohongshu.com 无水印/大图下载地址。
     *
     * @param url 输入：sns-webpic 原始 URL。
     * @return 输出：ci 站下载 URL；无法构建时返回 null。
     */
    fun buildCiDownloadUrl(url: String): String? {
        val path = CI_PATH_PATTERN.find(url)?.groupValues?.getOrNull(1) ?: return null
        return "https://ci.xiaohongshu.com/$path?imageView2/format/png"
    }

    /**
     * 判断 URL 是否为预览图路径。
     *
     * @param url 输入：图片 URL。
     * @return 输出：true 表示预览图。
     */
    fun isPreviewUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("webp_prv") ||
            lower.contains("!nc_n_webp_prv") ||
            lower.contains("!style_")
    }

    /**
     * 为下载请求生成候选 URL 列表（按优先级排序）。
     *
     * @param url 输入：解析得到的图片 URL。
     * @return 输出：候选下载 URL 列表。
     */
    fun getDownloadCandidates(url: String): List<String> {
        val upgraded = upgradePreviewToHd(url)
        val candidates = linkedSetOf<String>()
        candidates.add(UrlNormalizer.normalize(upgraded))
        if (upgraded != url) {
            candidates.add(UrlNormalizer.normalize(url))
        }
        buildCiDownloadUrl(url)?.let { candidates.add(it) }
        buildCiDownloadUrl(upgraded)?.let { candidates.add(it) }
        return candidates.toList()
    }

    /**
     * 从多个候选 URL 中选取最适合下载的一条。
     *
     * @param urls 输入：候选 URL 列表。
     * @return 输出：最佳下载 URL；列表为空时返回 null。
     */
    fun pickBestDownloadUrl(urls: List<String>): String? {
        return urls
            .filter { it.isNotBlank() }
            .maxByOrNull { downloadUrlRank(it) }
            ?.let { upgradePreviewToHd(it) }
    }

    /**
     * 比较两条 URL，返回更适合下载的一条。
     *
     * @param first 输入：URL A。
     * @param second 输入：URL B。
     * @return 输出：更优 URL。
     */
    fun preferBetter(first: String, second: String): String {
        return if (downloadUrlRank(first) >= downloadUrlRank(second)) first else second
    }

    /**
     * 计算下载 URL 优先级分数。
     *
     * @param url 输入：图片 URL。
     * @return 输出：分数越高越优先。
     */
    private fun downloadUrlRank(url: String): Int {
        var score = 0
        if (!isPreviewUrl(url)) score += 100
        if (url.contains("h5_1080", ignoreCase = true)) score += 80
        if (url.contains("webp_mw", ignoreCase = true)) score += 50
        if (url.contains("WB_DFT", ignoreCase = true)) score += 40
        if (url.contains("ci.xiaohongshu.com", ignoreCase = true)) score += 30
        if (url.contains("!style_", ignoreCase = true)) score -= 60
        score += url.length.coerceAtMost(200)
        return score
    }
}
