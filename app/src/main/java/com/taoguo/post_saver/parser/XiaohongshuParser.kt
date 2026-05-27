package com.taoguo.post_saver.parser

import com.taoguo.post_saver.model.MediaItem
import com.taoguo.post_saver.model.MediaType
import com.taoguo.post_saver.model.ParseResult
import com.taoguo.post_saver.model.Platform
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 小红书分享链接纯客户端解析器。
 */
class XiaohongshuParser(
    private val client: OkHttpClient = defaultClient(),
) : PlatformParser {

    /**
     * 解析小红书分享链接。
     *
     * @param url 输入：小红书分享链接或短链。
     * @return 输出：包含文案与媒体资源的解析结果。
     * @throws ParseException 解析失败时抛出。
     */
    override fun parse(url: String): ParseResult {
        val resolvedUrl = resolveFinalUrl(url)
        val noteId = extractNoteId(resolvedUrl)
            ?: throw ParseException("无法从链接中提取笔记 ID")

        val html = fetchHtml(buildExploreUrl(noteId, resolvedUrl))
        parseInitialState(html, noteId)?.let { return it }
        return parseOpenGraph(html, noteId)
    }

    /**
     * 跟随短链重定向，获取最终 URL。
     *
     * @param url 输入：原始链接。
     * @return 输出：重定向后的最终 URL。
     */
    private fun resolveFinalUrl(url: String): String {
        extractNoteId(url)?.let { noteId ->
            return buildExploreUrl(noteId, url)
        }

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", MOBILE_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code !in 300..399) {
                throw ParseException("链接访问失败（HTTP ${response.code}）")
            }
            return response.request.url.toString()
        }
    }

    /**
     * 从 URL 中提取笔记 ID。
     *
     * @param url 输入：小红书链接。
     * @return 输出：笔记 ID；无法提取时返回 null。
     */
    internal fun extractNoteId(url: String): String? {
        val patterns = listOf(
            Regex("""/(?:explore|discovery/item|item)/([a-f0-9]{24})""", RegexOption.IGNORE_CASE),
            Regex("""[?&]noteId=([a-f0-9]{24})""", RegexOption.IGNORE_CASE),
            Regex("""[?&]note_id=([a-f0-9]{24})""", RegexOption.IGNORE_CASE),
        )
        for (pattern in patterns) {
            pattern.find(url)?.groupValues?.getOrNull(1)?.let { return it.lowercase() }
        }
        return null
    }

    /**
     * 构建笔记探索页 URL。
     *
     * @param noteId 输入：笔记 ID。
     * @param originalUrl 输入：原始链接，用于保留 xsec_token 等参数。
     * @return 输出：探索页 URL。
     */
    private fun buildExploreUrl(noteId: String, originalUrl: String): String {
        val tokenMatch = Regex("""[?&]xsec_token=([^&]+)""").find(originalUrl)
        val tokenSuffix = tokenMatch?.groupValues?.getOrNull(1)?.let { "?xsec_token=$it" }.orEmpty()
        return "https://www.xiaohongshu.com/explore/$noteId$tokenSuffix"
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
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Referer", "https://www.xiaohongshu.com/")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ParseException("页面加载失败（HTTP ${response.code}）")
            }
            return response.body?.string().orEmpty()
        }
    }

    /**
     * 从 window.__INITIAL_STATE__ 解析笔记数据。
     *
     * @param html 输入：页面 HTML。
     * @param noteId 输入：笔记 ID。
     * @return 输出：解析结果；无法解析时返回 null。
     */
    private fun parseInitialState(html: String, noteId: String): ParseResult? {
        val rawJson = extractInitialStateJson(html) ?: return null
        return runCatching {
            val state = JSONObject(sanitizeJson(rawJson))
            val note = findNoteObject(state, noteId)
                ?: throw ParseException("页面中未找到笔记数据")
            mapNoteObject(note, noteId)
        }.getOrNull()
    }

    /**
     * 提取 __INITIAL_STATE__ JSON 文本。
     *
     * @param html 输入：页面 HTML。
     * @return 输出：JSON 字符串；未找到时返回 null。
     */
    private fun extractInitialStateJson(html: String): String? {
        val markers = listOf(
            "window.__INITIAL_STATE__=",
            "window.__INITIAL_SSR_STATE__=",
        )
        for (marker in markers) {
            val startIndex = html.indexOf(marker)
            if (startIndex < 0) continue
            val jsonStart = html.indexOf('{', startIndex + marker.length)
            if (jsonStart < 0) continue
            extractBalancedJson(html, jsonStart)?.let { return it }
        }
        return null
    }

    /**
     * 按花括号平衡规则截取 JSON 对象文本。
     *
     * @param text 输入：完整 HTML 或脚本文本。
     * @param start 输入：JSON 起始 `{` 的位置。
     * @return 输出：JSON 字符串；截取失败时返回 null。
     */
    private fun extractBalancedJson(text: String, start: Int): String? {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == '"') {
                    inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return text.substring(start, index + 1)
                    }
                }
            }
        }
        return null
    }

    /**
     * 清理 JSON 中 JavaScript 特有写法，便于 org.json 解析。
     *
     * @param raw 输入：原始 JSON 文本。
     * @return 输出：可解析的 JSON 文本。
     */
    private fun sanitizeJson(raw: String): String {
        return raw
            .trim()
            .trimEnd(';')
            .replace(":undefined", ":null")
            .replace(",undefined", ",null")
    }

    /**
     * 在状态 JSON 中查找笔记对象。
     *
     * @param state 输入：页面状态 JSON。
     * @param noteId 输入：目标笔记 ID。
     * @return 输出：笔记 JSON 对象；未找到时返回 null。
     */
    private fun findNoteObject(state: JSONObject, noteId: String): JSONObject? {
        val noteDetailMap = state.optJSONObject("note")?.optJSONObject("noteDetailMap")
            ?: state.optJSONObject("noteDetailMap")

        noteDetailMap?.optJSONObject(noteId)?.let { wrapper ->
            wrapper.optJSONObject("note")?.let { return it }
            if (wrapper.has("title") || wrapper.has("desc") || wrapper.has("imageList")) {
                return wrapper
            }
        }

        if (noteDetailMap != null) {
            val keys = noteDetailMap.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val wrapper = noteDetailMap.optJSONObject(key) ?: continue
                val note = wrapper.optJSONObject("note") ?: wrapper
                if (note.has("imageList") || note.has("video") || note.has("desc")) {
                    return note
                }
            }
        }

        return findNoteRecursive(state, noteId)
    }

    /**
     * 递归搜索 JSON 中的笔记对象。
     *
     * @param json 输入：当前 JSON 节点。
     * @param noteId 输入：目标笔记 ID。
     * @return 输出：笔记 JSON 对象；未找到时返回 null。
     */
    private fun findNoteRecursive(json: Any?, noteId: String): JSONObject? {
        when (json) {
            is JSONObject -> {
                val currentId = json.optString("noteId", json.optString("id"))
                if (currentId.equals(noteId, ignoreCase = true) &&
                    (json.has("imageList") || json.has("video") || json.has("desc"))
                ) {
                    return json
                }
                val keys = json.keys()
                while (keys.hasNext()) {
                    findNoteRecursive(json.opt(keys.next()), noteId)?.let { return it }
                }
            }
            is JSONArray -> {
                for (index in 0 until json.length()) {
                    findNoteRecursive(json.opt(index), noteId)?.let { return it }
                }
            }
        }
        return null
    }

    /**
     * 从 Open Graph 元信息兜底解析。
     *
     * @param html 输入：页面 HTML。
     * @param noteId 输入：笔记 ID。
     * @return 输出：解析结果。
     */
    private fun parseOpenGraph(html: String, noteId: String): ParseResult {
        val caption = extractMetaContent(html, "og:description")
            ?: extractMetaContent(html, "description")
            ?: ""
        val author = extractMetaContent(html, "og:site_name")

        val mediaItems = mutableListOf<MediaItem>()
        extractMetaContent(html, "og:video")?.let { videoUrl ->
            mediaItems.add(
                MediaItem(
                    type = MediaType.VIDEO,
                    url = videoUrl,
                    previewUrl = extractMetaContent(html, "og:image"),
                    fileName = "xhs_${noteId}.mp4",
                    referer = XHS_REFERER,
                ),
            )
        }

        val imageUrl = extractMetaContent(html, "og:image")
        if (imageUrl != null && mediaItems.none { it.type == MediaType.VIDEO }) {
            mediaItems.add(
                MediaItem(
                    type = MediaType.IMAGE,
                    url = imageUrl,
                    previewUrl = imageUrl,
                    fileName = "xhs_${noteId}_1.jpg",
                    referer = XHS_REFERER,
                ),
            )
        }

        if (mediaItems.isEmpty()) {
            throw ParseException("无法解析页面数据，小红书页面结构可能已变更")
        }

        return ParseResult(
            platform = Platform.XIAOHONGSHU,
            caption = caption,
            author = author,
            mediaItems = mediaItems,
        )
    }

    /**
     * 提取 meta 标签 content 属性。
     *
     * @param html 输入：HTML 内容。
     * @param property 输入：meta property 或 name。
     * @return 输出：content 值；未找到时返回 null。
     */
    private fun extractMetaContent(html: String, property: String): String? {
        val patterns = listOf(
            Regex("""<meta[^>]+property="$property"[^>]+content="([^"]+)"""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+content="([^"]+)"[^>]+property="$property"""", RegexOption.IGNORE_CASE),
            Regex("""<meta[^>]+name="$property"[^>]+content="([^"]+)"""", RegexOption.IGNORE_CASE),
        )
        for (pattern in patterns) {
            pattern.find(html)?.groupValues?.getOrNull(1)?.let { value ->
                if (value.isNotBlank()) return decodeHtmlEntities(value)
            }
        }
        return null
    }

    /**
     * 解码常见 HTML 实体。
     *
     * @param value 输入：含实体的字符串。
     * @return 输出：解码后的字符串。
     */
    private fun decodeHtmlEntities(value: String): String {
        return value
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    /**
     * 将笔记 JSON 映射为 ParseResult。
     *
     * @param note 输入：笔记 JSON 对象。
     * @param noteId 输入：笔记 ID。
     * @return 输出：解析结果。
     */
    private fun mapNoteObject(note: JSONObject, noteId: String): ParseResult {
        val title = note.optString("title")
        val desc = note.optString("desc")
        val caption = listOf(title, desc).filter { it.isNotBlank() }.joinToString("\n")
        val author = note.optJSONObject("user")?.optString("nickname")?.ifBlank { null }
            ?: note.optJSONObject("user")?.optString("nickName")?.ifBlank { null }

        val mediaItems = buildMediaItems(note, noteId)
        if (mediaItems.isEmpty()) {
            throw ParseException("未找到可下载的图片或视频")
        }

        return ParseResult(
            platform = Platform.XIAOHONGSHU,
            caption = caption,
            author = author,
            mediaItems = mediaItems,
        )
    }

    /**
     * 从笔记 JSON 构建媒体列表。
     *
     * @param note 输入：笔记 JSON 对象。
     * @param noteId 输入：笔记 ID。
     * @return 输出：媒体资源列表。
     */
    private fun buildMediaItems(note: JSONObject, noteId: String): List<MediaItem> {
        val items = mutableListOf<MediaItem>()

        val imageList = note.optJSONArray("imageList")
        if (imageList != null && imageList.length() > 0) {
            for (index in 0 until imageList.length()) {
                val imageObj = imageList.optJSONObject(index) ?: continue
                val imageUrl = pickImageUrl(imageObj) ?: continue
                items.add(
                    MediaItem(
                        type = MediaType.IMAGE,
                        url = imageUrl,
                        previewUrl = imageUrl,
                        fileName = "xhs_${noteId}_${index + 1}.jpg",
                        referer = XHS_REFERER,
                    ),
                )
            }
        }

        val videoObj = note.optJSONObject("video")
        if (videoObj != null) {
            val videoUrl = pickVideoUrl(videoObj)
            val coverUrl = videoObj.optString("cover").ifBlank { null }
                ?: videoObj.optJSONObject("cover")?.optString("urlDefault")?.ifBlank { null }
            if (videoUrl != null) {
                items.add(
                    MediaItem(
                        type = MediaType.VIDEO,
                        url = videoUrl,
                        previewUrl = coverUrl,
                        fileName = "xhs_${noteId}.mp4",
                        referer = XHS_REFERER,
                    ),
                )
            }
        }

        return items
    }

    /**
     * 从图片 JSON 中选取最佳 URL。
     *
     * @param imageObj 输入：图片 JSON 对象。
     * @return 输出：图片 URL；未找到时返回 null。
     */
    private fun pickImageUrl(imageObj: JSONObject): String? {
        val candidates = listOf(
            imageObj.optString("urlDefault"),
            imageObj.optString("url"),
            imageObj.optString("original"),
            imageObj.optString("livePhoto"),
        ).filter { it.isNotBlank() }

        imageObj.optJSONArray("infoList")?.let { infoList ->
            for (index in 0 until infoList.length()) {
                infoList.optJSONObject(index)?.optString("url")?.takeIf { it.isNotBlank() }?.let {
                    return it
                }
            }
        }

        return candidates.firstOrNull()
    }

    /**
     * 从视频 JSON 中选取最佳播放地址。
     *
     * @param videoObj 输入：视频 JSON 对象。
     * @return 输出：视频 URL；未找到时返回 null。
     */
    private fun pickVideoUrl(videoObj: JSONObject): String? {
        videoObj.optJSONObject("media")?.optJSONObject("stream")?.let { stream ->
            pickStreamUrl(stream.optJSONArray("h265"))?.let { return it }
            pickStreamUrl(stream.optJSONArray("h264"))?.let { return it }
            pickStreamUrl(stream.optJSONArray("av1"))?.let { return it }
        }

        videoObj.optString("media").takeIf { it.startsWith("http") }?.let { return it }
        videoObj.optJSONObject("consumer")?.optString("originVideoKey")?.takeIf { it.isNotBlank() }?.let { key ->
            return "https://sns-video-bd.xhscdn.com/$key"
        }

        return null
    }

    /**
     * 从 stream 数组中提取 masterUrl。
     *
     * @param streams 输入：stream JSON 数组。
     * @return 输出：视频 URL；未找到时返回 null。
     */
    private fun pickStreamUrl(streams: JSONArray?): String? {
        if (streams == null || streams.length() == 0) return null
        for (index in 0 until streams.length()) {
            val stream = streams.optJSONObject(index) ?: continue
            stream.optString("masterUrl").takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    companion object {
        private const val XHS_REFERER = "https://www.xiaohongshu.com/"
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
