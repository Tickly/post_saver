package com.taoguo.post_saver.parser

import com.taoguo.post_saver.model.MediaItem
import com.taoguo.post_saver.model.MediaType
import com.taoguo.post_saver.model.ParseResult
import com.taoguo.post_saver.model.Platform
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/**
 * 抖音分享链接纯客户端解析器。
 */
class DouyinParser(
    private val client: OkHttpClient = defaultClient(),
) : PlatformParser {

    /**
     * 解析抖音分享链接。
     *
     * @param url 输入：抖音分享链接或短链。
     * @return 输出：包含文案与媒体资源的解析结果。
     * @throws ParseException 解析失败时抛出。
     */
    override fun parse(url: String): ParseResult {
        val resolvedUrl = resolveFinalUrl(url)
        val itemId = extractItemId(resolvedUrl)
            ?: throw ParseException("无法从链接中提取作品 ID")

        fetchItemInfo(itemId)?.let { return it }

        val sharePageUrl = "https://www.iesdouyin.com/share/video/$itemId"
        val html = fetchHtml(sharePageUrl)
        return parseEmbeddedJson(html, itemId)
    }

    /**
     * 跟随短链重定向，获取最终 URL。
     *
     * @param url 输入：原始链接。
     * @return 输出：重定向后的最终 URL。
     */
    private fun resolveFinalUrl(url: String): String {
        extractItemId(url)?.let { id ->
            return "https://www.douyin.com/video/$id"
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", MOBILE_USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code !in 300..399) {
                throw ParseException("链接访问失败（HTTP ${response.code}）")
            }
            return response.request.url.toString()
        }
    }

    /**
     * 从 URL 中提取作品 ID。
     *
     * @param url 输入：抖音链接。
     * @return 输出：作品 ID；无法提取时返回 null。
     */
    internal fun extractItemId(url: String): String? {
        val patterns = listOf(
            Regex("""/video/(\d+)"""),
            Regex("""/note/(\d+)"""),
            Regex("""/share/video/(\d+)"""),
            Regex("""[?&]modal_id=(\d+)"""),
            Regex("""[?&]aweme_id=(\d+)"""),
        )
        for (pattern in patterns) {
            pattern.find(url)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return null
    }

    /**
     * 调用抖音分享页 iteminfo 接口获取作品信息。
     *
     * @param itemId 输入：作品 ID。
     * @return 输出：解析结果；接口不可用时返回 null。
     */
    private fun fetchItemInfo(itemId: String): ParseResult? {
        val apiUrl = "https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/?item_ids=$itemId"
        val request = Request.Builder()
            .url(apiUrl)
            .header("User-Agent", MOBILE_USER_AGENT)
            .header("Referer", "https://www.douyin.com/")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return null
                parseItemInfoJson(body)
            }
        }.getOrNull()
    }

    /**
     * 拉取 HTML 页面内容。
     *
     * @param url 输入：页面 URL。
     * @return 输出：HTML 字符串。
     */
    private fun fetchHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", MOBILE_USER_AGENT)
            .header("Referer", "https://www.douyin.com/")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ParseException("页面加载失败（HTTP ${response.code}）")
            }
            return response.body?.string().orEmpty()
        }
    }

    /**
     * 解析 iteminfo 接口 JSON。
     *
     * @param json 输入：接口返回 JSON 字符串。
     * @return 输出：解析结果。
     */
    private fun parseItemInfoJson(json: String): ParseResult {
        val root = JSONObject(json)
        val itemList = root.optJSONArray("item_list")
            ?: throw ParseException("接口未返回作品数据")
        if (itemList.length() == 0) {
            throw ParseException("作品不存在或已被删除")
        }
        return mapAwemeItem(itemList.getJSONObject(0))
    }

    /**
     * 从 HTML 内嵌 JSON 中解析作品信息。
     *
     * @param html 输入：分享页 HTML。
     * @param itemId 输入：作品 ID，用于兜底校验。
     * @return 输出：解析结果。
     */
    private fun parseEmbeddedJson(html: String, itemId: String): ParseResult {
        val jsonCandidates = listOf(
            extractScriptJson(html, "RENDER_DATA"),
            extractRouterData(html),
            extractRegexJson(html, """window\._SSR_HYDRATED_DATA\s*=\s*(.+?)</script>"""),
        ).filterNotNull()

        for (candidate in jsonCandidates) {
            runCatching {
                val decoded = URLDecoder.decode(candidate, Charsets.UTF_8.name())
                val json = if (decoded.startsWith("{")) decoded else candidate
                findAwemeInJson(JSONObject(json), itemId)?.let { return mapAwemeItem(it) }
            }
        }

        throw ParseException("无法解析页面数据，抖音页面结构可能已变更")
    }

    /**
     * 提取 script 标签中的 JSON 文本。
     *
     * @param html 输入：HTML 内容。
     * @param scriptId 输入：script 标签 id。
     * @return 输出：JSON 字符串；未找到时返回 null。
     */
    private fun extractScriptJson(html: String, scriptId: String): String? {
        val pattern = Regex(
            """<script[^>]*id="$scriptId"[^>]*>([\s\S]*?)</script>""",
            RegexOption.IGNORE_CASE,
        )
        return pattern.find(html)?.groupValues?.getOrNull(1)?.trim()
    }

    /**
     * 提取 _ROUTER_DATA 内嵌 JSON。
     *
     * @param html 输入：HTML 内容。
     * @return 输出：JSON 字符串；未找到时返回 null。
     */
    private fun extractRouterData(html: String): String? {
        return extractRegexJson(html, """window\._ROUTER_DATA\s*=\s*(.+?)</script>""")
    }

    /**
     * 按正则从 HTML 中提取 JSON 片段。
     *
     * @param html 输入：HTML 内容。
     * @param pattern 输入：正则表达式。
     * @return 输出：JSON 字符串；未找到时返回 null。
     */
    private fun extractRegexJson(html: String, pattern: String): String? {
        return Regex(pattern, RegexOption.DOT_MATCHES_ALL)
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.trimEnd(';')
    }

    /**
     * 在嵌套 JSON 中查找 aweme 对象。
     *
     * @param json 输入：JSON 对象根节点。
     * @param itemId 输入：目标作品 ID。
     * @return 输出：aweme JSONObject；未找到时返回 null。
     */
    private fun findAwemeInJson(json: JSONObject, itemId: String): JSONObject? {
        if (json.has("aweme_id") || json.has("awemeId")) {
            val id = json.optString("aweme_id", json.optString("awemeId"))
            if (id.isBlank() || id == itemId) return json
        }
        if (json.has("aweme_detail")) {
            return json.optJSONObject("aweme_detail")
        }
        if (json.has("awemeDetail")) {
            return json.optJSONObject("awemeDetail")
        }

        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            when (val value = json.opt(key)) {
                is JSONObject -> findAwemeInJson(value, itemId)?.let { return it }
                is JSONArray -> {
                    for (i in 0 until value.length()) {
                        val element = value.optJSONObject(i) ?: continue
                        findAwemeInJson(element, itemId)?.let { return it }
                    }
                }
            }
        }
        return null
    }

    /**
     * 将 aweme JSON 映射为 ParseResult。
     *
     * @param item 输入：aweme JSON 对象。
     * @return 输出：解析结果。
     */
    private fun mapAwemeItem(item: JSONObject): ParseResult {
        val caption = item.optString("desc").ifBlank { item.optString("title") }
        val author = item.optJSONObject("author")?.optString("nickname")?.ifBlank { null }
        val mediaItems = buildMediaItems(item)
        if (mediaItems.isEmpty()) {
            throw ParseException("未找到可下载的图片或视频")
        }
        return ParseResult(
            platform = Platform.DOUYIN,
            caption = caption,
            author = author,
            mediaItems = mediaItems,
        )
    }

    /**
     * 从 aweme JSON 构建媒体列表。
     *
     * @param item 输入：aweme JSON 对象。
     * @return 输出：媒体资源列表。
     */
    private fun buildMediaItems(item: JSONObject): List<MediaItem> {
        val awemeId = item.optString("aweme_id", item.optString("awemeId", "unknown"))
        val items = mutableListOf<MediaItem>()

        val images = item.optJSONArray("images")
        val hasImages = images != null && images.length() > 0
        val awemeType = item.optInt("aweme_type", item.optInt("awemeType", -1))
        val isImagePost = hasImages || awemeType in IMAGE_AWEME_TYPES

        if (hasImages) {
            for (index in 0 until images!!.length()) {
                val imageObj = images.optJSONObject(index) ?: continue
                val imageUrl = pickBestUrl(imageObj.optJSONArray("url_list"))
                    ?: pickBestUrl(imageObj.optJSONObject("download_url_list")?.optJSONArray("url_list"))
                if (imageUrl != null) {
                    items.add(
                        MediaItem(
                            type = MediaType.IMAGE,
                            url = imageUrl,
                            previewUrl = imageUrl,
                            fileName = "douyin_${awemeId}_${index + 1}.jpg",
                        ),
                    )
                }
            }
        }

        if (!isImagePost) {
            val video = item.optJSONObject("video")
            if (video != null) {
                val playUrl = pickBestUrl(video.optJSONObject("play_addr")?.optJSONArray("url_list"))
                    ?: pickBestUrl(video.optJSONObject("download_addr")?.optJSONArray("url_list"))
                val coverUrl = pickBestUrl(video.optJSONObject("cover")?.optJSONArray("url_list"))
                    ?: pickBestUrl(video.optJSONObject("origin_cover")?.optJSONArray("url_list"))
                if (playUrl != null && isVideoUrl(playUrl)) {
                    items.add(
                        MediaItem(
                            type = MediaType.VIDEO,
                            url = playUrl,
                            previewUrl = coverUrl,
                            fileName = "douyin_${awemeId}.mp4",
                        ),
                    )
                }
            }
        }

        return items
    }

    /**
     * 判断 URL 是否为视频资源（排除误把图片地址当作视频的情况）。
     *
     * @param url 输入：待检测 URL。
     * @return 输出：true 表示像视频地址；false 表示像图片或非视频地址。
     */
    private fun isVideoUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (IMAGE_URL_HINTS.any { lower.contains(it) }) {
            return false
        }
        return VIDEO_URL_HINTS.any { lower.contains(it) }
    }

    /**
     * 从 url_list 中选取最佳 URL（优先无水印）。
     *
     * @param urlList 输入：URL 数组。
     * @return 输出：最佳 URL；列表为空时返回 null。
     */
    private fun pickBestUrl(urlList: JSONArray?): String? {
        if (urlList == null || urlList.length() == 0) return null
        val urls = (0 until urlList.length()).mapNotNull { index ->
            urlList.optString(index).takeIf { it.isNotBlank() }
        }
        return urls.firstOrNull { !it.contains("watermark", ignoreCase = true) }
            ?: urls.firstOrNull()
    }

    companion object {
        private val IMAGE_AWEME_TYPES = setOf(2, 68, 61)
        private val IMAGE_URL_HINTS = listOf(".jpg", ".jpeg", ".webp", ".png", ".heic", "/image/")
        private val VIDEO_URL_HINTS = listOf(".mp4", "/video/", "mime_type=video", "mime=video", "video/tos")
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/604.1"

        /**
         * 创建默认 OkHttp 客户端。
         *
         * @return 输出：带超时与重定向配置的客户端。
         */
        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()
        }
    }
}
