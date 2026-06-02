package com.taoguo.post_saver.parser

import com.taoguo.post_saver.debug.ParseDebugJson
import com.taoguo.post_saver.debug.ParseDebugStore
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
        val debug = JSONObject()
        try {
            val normalizedUrl = UrlNormalizer.normalize(url)
            val resolvedUrl = resolveFinalUrl(normalizedUrl)
            val itemId = extractItemId(resolvedUrl)
                ?: throw ParseException("无法从链接中提取作品 ID")
            debug.put("inputUrl", normalizedUrl)

            fetchItemInfo(itemId, normalizedUrl, resolvedUrl)?.let { return it }

            val sharePageUrl = "https://www.iesdouyin.com/share/video/$itemId"
            val html = fetchHtml(sharePageUrl)
            return parseEmbeddedJson(html, itemId, normalizedUrl, resolvedUrl)
        } catch (error: Exception) {
            debug.put("error", error.message ?: error.javaClass.simpleName)
            ParseDebugStore.set(debug.toString(2))
            throw error
        }
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
    private fun fetchItemInfo(itemId: String, inputUrl: String, resolvedUrl: String): ParseResult? {
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
                parseItemInfoJson(body, inputUrl, resolvedUrl)
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
    private fun parseItemInfoJson(json: String, inputUrl: String, resolvedUrl: String): ParseResult {
        val root = JSONObject(json)
        val itemList = root.optJSONArray("item_list")
            ?: throw ParseException("接口未返回作品数据")
        if (itemList.length() == 0) {
            throw ParseException("作品不存在或已被删除")
        }
        val aweme = itemList.getJSONObject(0)
        return mapAwemeItem(aweme, "iteminfo", resolvedUrl, inputUrl)
    }

    /**
     * 从 HTML 内嵌 JSON 中解析作品信息。
     *
     * @param html 输入：分享页 HTML。
     * @param itemId 输入：作品 ID，用于兜底校验。
     * @param inputUrl 输入：用户输入的归一化链接。
     * @param resolvedUrl 输入：重定向后的最终链接。
     * @return 输出：解析结果。
     */
    private fun parseEmbeddedJson(
        html: String,
        itemId: String,
        inputUrl: String,
        resolvedUrl: String,
    ): ParseResult {
        val jsonCandidates = listOf(
            extractScriptJson(html, "RENDER_DATA"),
            extractRouterData(html),
            extractRegexJson(html, """window\._SSR_HYDRATED_DATA\s*=\s*(.+?)</script>"""),
        ).filterNotNull()

        for (candidate in jsonCandidates) {
            runCatching {
                val decoded = URLDecoder.decode(candidate, Charsets.UTF_8.name())
                val json = if (decoded.startsWith("{")) decoded else candidate
                findAwemeInJson(JSONObject(json), itemId)?.let {
                    return mapAwemeItem(it, "embeddedHtml", resolvedUrl, inputUrl)
                }
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
     * 将 aweme JSON 映射为 ParseResult，并写入调试 JSON 缓存。
     *
     * @param item 输入：aweme JSON 对象。
     * @param parseSource 输入：解析来源标识。
     * @param resolvedUrl 输入：重定向后的最终链接。
     * @param inputUrl 输入：用户输入的归一化链接。
     * @return 输出：解析结果。
     */
    private fun mapAwemeItem(
        item: JSONObject,
        parseSource: String,
        resolvedUrl: String,
        inputUrl: String,
    ): ParseResult {
        val caption = item.optString("desc").ifBlank { item.optString("title") }
        val author = item.optJSONObject("author")?.optString("nickname")?.ifBlank { null }
        val mediaItems = buildMediaItems(item)
        if (mediaItems.isEmpty()) {
            throw ParseException("未找到可下载的图片或视频")
        }
        val result = ParseResult(
            platform = Platform.DOUYIN,
            caption = caption,
            author = author,
            mediaItems = mediaItems,
        )
        val itemId = item.optString("aweme_id", item.optString("awemeId"))
        val debug = JSONObject().put("inputUrl", inputUrl)
        ParseDebugJson.putDouyinFields(debug, itemId, resolvedUrl, item, parseSource)
        ParseDebugJson.putParseResult(debug, result)
        ParseDebugStore.set(debug.toString(2))
        return result
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

        val video = item.optJSONObject("video")
        val videoVariants = extractVideoVariants(video)
        val contentType = classifyContentType(item, videoVariants)

        val images = item.optJSONArray("images")
        val hasImages = images != null && images.length() > 0

        if (contentType == DouyinContentType.IMAGE && hasImages) {
            for (index in 0 until images!!.length()) {
                val imageObj = images.optJSONObject(index) ?: continue
                val imageUrl = pickImageUrl(imageObj) ?: continue
                items.add(createImageItem(awemeId, index, imageUrl))
            }
        }

        if (contentType == DouyinContentType.IMAGE && items.isEmpty()) {
            extractCoverImages(item, awemeId).let { items.addAll(it) }
        }

        if (contentType == DouyinContentType.IMAGE && items.isEmpty()) {
            extractFromImageInfos(item, awemeId).let { items.addAll(it) }
        }

        if (contentType == DouyinContentType.VIDEO && videoVariants.isNotEmpty()) {
            val coverUrl = pickBestImageUrl(video?.optJSONObject("cover")?.optJSONArray("url_list"))
                ?: pickBestImageUrl(video?.optJSONObject("origin_cover")?.optJSONArray("url_list"))
                ?: pickBestImageUrl(video?.optJSONObject("dynamic_cover")?.optJSONArray("url_list"))
            for (variant in videoVariants) {
                items.add(
                    MediaItem(
                        type = MediaType.VIDEO,
                        url = variant.url,
                        previewUrl = coverUrl,
                        fileName = "douyin_${awemeId}_${variant.fileSuffix}.mp4",
                        referer = DOUYIN_REFERER,
                        label = variant.label,
                    ),
                )
            }
        }

        return items
    }

    /**
     * 判断作品应作为图片还是视频处理。
     *
     * @param item 输入：aweme JSON 对象。
     * @param videoVariants 输入：已提取的视频变体列表。
     * @return 输出：内容类型枚举。
     */
    private fun classifyContentType(item: JSONObject, videoVariants: List<VideoVariant>): DouyinContentType {
        val awemeType = item.optInt("aweme_type", item.optInt("awemeType", -1))
        val duration = item.optJSONObject("video")?.optInt("duration", 0) ?: 0
        val hasImages = (item.optJSONArray("images")?.length() ?: 0) > 0
        val hasVideoVariants = videoVariants.isNotEmpty()

        if (awemeType in IMAGE_AWEME_TYPES) {
            return DouyinContentType.IMAGE
        }
        if (awemeType in VIDEO_AWEME_TYPES && duration > 0 && hasVideoVariants) {
            return DouyinContentType.VIDEO
        }
        if (hasImages && (!hasVideoVariants || duration == 0)) {
            return DouyinContentType.IMAGE
        }
        if (hasVideoVariants && duration > 0) {
            return DouyinContentType.VIDEO
        }
        if (hasImages) {
            return DouyinContentType.IMAGE
        }
        if (hasVideoVariants) {
            return DouyinContentType.VIDEO
        }
        return DouyinContentType.IMAGE
    }

    /**
     * 从 video 对象中提取全部可下载的视频变体。
     *
     * @param video 输入：video JSON 对象。
     * @return 输出：按优先级排序的去重变体列表。
     */
    private fun extractVideoVariants(video: JSONObject?): List<VideoVariant> {
        if (video == null) {
            return emptyList()
        }

        val variantMap = linkedMapOf<String, VideoVariant>()

        collectVariantsFromUrlList(
            variantMap,
            video.optJSONObject("play_addr")?.optJSONArray("url_list"),
        )
        collectVariantsFromUrlList(
            variantMap,
            video.optJSONObject("play_addr_lowbr")?.optJSONArray("url_list"),
            LABEL_LOWBR,
            SORT_LOWBR,
        )
        collectVariantsFromUrlList(
            variantMap,
            video.optJSONObject("play_addr_h264")?.optJSONArray("url_list"),
            LABEL_H264,
            SORT_H264,
        )
        collectVariantsFromUrlList(
            variantMap,
            video.optJSONObject("play_addr_265")?.optJSONArray("url_list"),
            LABEL_H265,
            SORT_H265,
        )
        collectVariantsFromUrlList(
            variantMap,
            video.optJSONObject("download_addr")?.optJSONArray("url_list"),
            LABEL_DOWNLOAD,
            SORT_DOWNLOAD,
        )
        collectVariantsFromBitRate(variantMap, video.optJSONArray("bit_rate"))

        val playAddr = video.optJSONObject("play_addr")
        val urlList = playAddr?.optJSONArray("url_list")
        val hasUrlList = urlList != null && urlList.length() > 0
        val uri = playAddr?.optString("uri")?.takeIf { it.isNotBlank() }
        if (uri != null && !hasUrlList) {
            registerVariant(
                variantMap,
                buildPlayUrlFromVideoId(uri),
                LABEL_API_NOWM,
                SORT_API_NOWM,
            )
            registerVariant(
                variantMap,
                buildPlaywmUrlFromVideoId(uri),
                LABEL_API_WM,
                SORT_API_WM,
            )
        }

        return variantMap.values.sortedBy { it.sortOrder }
    }

    /**
     * 从 url_list 收集视频变体并写入映射表。
     *
     * @param variantMap 输入/输出：规范化 URL 到变体的映射。
     * @param urlList 输入：URL 数组。
     * @param defaultLabel 输入：无法自动分类时的默认标签，可为空。
     * @param defaultSortOrder 输入：默认排序权重。
     * @return 输出：无返回值。
     */
    private fun collectVariantsFromUrlList(
        variantMap: LinkedHashMap<String, VideoVariant>,
        urlList: JSONArray?,
        defaultLabel: String? = null,
        defaultSortOrder: Int = SORT_OTHER,
    ) {
        for (url in collectUrls(urlList)) {
            if (url.contains("playwm", ignoreCase = true)) {
                registerVariant(variantMap, url, LABEL_API_WM, SORT_API_WM)
                registerVariant(
                    variantMap,
                    normalizeVideoPlayUrl(url),
                    LABEL_API_NOWM,
                    SORT_API_NOWM,
                )
                continue
            }

            val classified = classifyVideoUrl(url)
            if (classified != null) {
                registerVariant(variantMap, url, classified.first, classified.second)
            } else if (defaultLabel != null) {
                registerVariant(variantMap, url, defaultLabel, defaultSortOrder)
            }
        }
    }

    /**
     * 从 bit_rate 数组收集视频变体。
     *
     * @param variantMap 输入/输出：规范化 URL 到变体的映射。
     * @param bitRateList 输入：bit_rate JSON 数组。
     * @return 输出：无返回值。
     */
    private fun collectVariantsFromBitRate(
        variantMap: LinkedHashMap<String, VideoVariant>,
        bitRateList: JSONArray?,
    ) {
        if (bitRateList == null || bitRateList.length() == 0) {
            return
        }
        for (index in 0 until bitRateList.length()) {
            val bitRate = bitRateList.optJSONObject(index) ?: continue
            val gearName = bitRate.optString("gear_name").ifBlank {
                val bitRateValue = bitRate.optInt("bit_rate", 0)
                if (bitRateValue > 0) "${bitRateValue}bps" else "码率"
            }
            collectVariantsFromUrlList(
                variantMap,
                bitRate.optJSONObject("play_addr")?.optJSONArray("url_list"),
                gearName,
                SORT_BITRATE,
            )
        }
    }

    /**
     * 注册单个视频变体，相同 URL 保留排序权重更高者。
     *
     * @param variantMap 输入/输出：规范化 URL 到变体的映射。
     * @param url 输入：原始视频 URL。
     * @param label 输入：变体标签。
     * @param sortOrder 输入：排序权重（越小越靠前）。
     * @return 输出：无返回值。
     */
    private fun registerVariant(
        variantMap: LinkedHashMap<String, VideoVariant>,
        url: String,
        label: String,
        sortOrder: Int,
    ) {
        if (isImageUrl(url)) {
            return
        }
        val normalized = UrlNormalizer.normalize(url)
        val existing = variantMap[normalized]
        if (existing == null || sortOrder < existing.sortOrder) {
            variantMap[normalized] = VideoVariant(
                url = normalized,
                label = label,
                sortOrder = sortOrder,
                fileSuffix = fileSuffixForLabel(label),
            )
        }
    }

    /**
     * 根据 URL 特征推断变体标签与排序权重。
     *
     * @param url 输入：候选视频 URL。
     * @return 输出：标签与排序权重对；无法识别时返回 null。
     */
    private fun classifyVideoUrl(url: String): Pair<String, Int>? {
        if (isImageUrl(url)) {
            return null
        }
        val lower = url.lowercase()
        return when {
            lower.contains("playwm") -> LABEL_API_WM to SORT_API_WM
            lower.contains("/play/") || lower.contains("/play?") -> LABEL_API_NOWM to SORT_API_NOWM
            lower.contains("douyinvod") && lower.contains("watermark") -> LABEL_CDN_WM to SORT_CDN_WM
            lower.contains("douyinvod") && lower.contains("/clean/") -> LABEL_CDN_CLEAN to SORT_CDN_CLEAN
            lower.contains("douyinvod") -> LABEL_CDN_CLEAN to SORT_CDN_CLEAN
            lower.contains("watermark") -> LABEL_CDN_WM to SORT_CDN_WM
            else -> null
        }
    }

    /**
     * 根据变体标签生成文件名后缀。
     *
     * @param label 输入：变体标签。
     * @return 输出：文件名后缀片段。
     */
    private fun fileSuffixForLabel(label: String): String {
        return when (label) {
            LABEL_CDN_CLEAN -> "cdn_clean"
            LABEL_CDN_WM -> "cdn_wm"
            LABEL_API_NOWM -> "api_nowm"
            LABEL_API_WM -> "api_wm"
            LABEL_LOWBR -> "lowbr"
            LABEL_H264 -> "h264"
            LABEL_H265 -> "h265"
            LABEL_DOWNLOAD -> "download"
            else -> label.replace(Regex("[^a-zA-Z0-9]+"), "_").trim('_').lowercase().ifBlank { "variant" }
        }
    }

    /**
     * 从 video 对象中提取首选视频 URL（兼容旧逻辑）。
     *
     * @param video 输入：video JSON 对象。
     * @return 输出：优先级最高的视频 URL；未找到时返回 null。
     */
    private fun extractVideoPlayUrl(video: JSONObject?): String? {
        return extractVideoVariants(video).firstOrNull()?.url
    }

    /**
     * 根据 video_id 构造无水印播放 API 地址。
     *
     * @param videoId 输入：play_addr.uri 或同类视频 ID。
     * @return 输出：aweme play 接口 URL。
     */
    private fun buildPlayUrlFromVideoId(videoId: String): String {
        return "https://aweme.snssdk.com/aweme/v1/play/?video_id=$videoId&ratio=720p&line=0"
    }

    /**
     * 根据 video_id 构造带水印播放 API 地址。
     *
     * @param videoId 输入：play_addr.uri 或同类视频 ID。
     * @return 输出：aweme playwm 接口 URL。
     */
    private fun buildPlaywmUrlFromVideoId(videoId: String): String {
        return "https://aweme.snssdk.com/aweme/v1/playwm/?video_id=$videoId&ratio=720p&line=0"
    }

    /**
     * 将 playwm 播放地址转换为 play（无水印 API 路径）。
     *
     * @param url 输入：原始视频 URL。
     * @return 输出：替换 playwm 后的 URL。
     */
    private fun normalizeVideoPlayUrl(url: String): String {
        return PLAYWM_PATTERN.replace(url, "play")
    }

    /**
     * 从单张图片 JSON 中提取最佳图片 URL。
     *
     * @param imageObj 输入：图片 JSON 对象。
     * @return 输出：图片 URL；未找到时返回 null。
     */
    private fun pickImageUrl(imageObj: JSONObject): String? {
        return pickBestUrlFromDownloadList(imageObj.optJSONArray("download_url_list"))
            ?: pickBestImageUrl(imageObj.optJSONArray("url_list"))
            ?: imageObj.optString("download_url").takeIf { it.isNotBlank() }?.let { UrlNormalizer.normalize(it) }
    }

    /**
     * 从封面字段提取图片（图文单图常见结构）。
     *
     * @param item 输入：aweme JSON 对象。
     * @param awemeId 输入：作品 ID。
     * @return 输出：图片媒体列表。
     */
    private fun extractCoverImages(item: JSONObject, awemeId: String): List<MediaItem> {
        val video = item.optJSONObject("video") ?: return emptyList()
        val coverUrl = pickBestImageUrl(video.optJSONObject("cover")?.optJSONArray("url_list"))
            ?: pickBestImageUrl(video.optJSONObject("origin_cover")?.optJSONArray("url_list"))
            ?: pickBestImageUrl(video.optJSONObject("dynamic_cover")?.optJSONArray("url_list"))
        return coverUrl?.let { listOf(createImageItem(awemeId, 0, it)) } ?: emptyList()
    }

    /**
     * 从 image_infos 字段提取图片列表。
     *
     * @param item 输入：aweme JSON 对象。
     * @param awemeId 输入：作品 ID。
     * @return 输出：图片媒体列表。
     */
    private fun extractFromImageInfos(item: JSONObject, awemeId: String): List<MediaItem> {
        val imageInfos = item.optJSONArray("image_infos") ?: return emptyList()
        val results = mutableListOf<MediaItem>()
        for (index in 0 until imageInfos.length()) {
            val info = imageInfos.optJSONObject(index) ?: continue
            val url = pickBestImageUrl(info.optJSONArray("url_list"))
                ?: pickBestImageUrl(info.optJSONObject("label_large")?.optJSONArray("url_list"))
                ?: pickBestImageUrl(info.optJSONObject("label_thumb")?.optJSONArray("url_list"))
            if (url != null) {
                results.add(createImageItem(awemeId, index, url))
            }
        }
        return results
    }

    /**
     * 创建抖音图片媒体项。
     *
     * @param awemeId 输入：作品 ID。
     * @param index 输入：图片序号。
     * @param rawUrl 输入：原始图片 URL。
     * @return 输出：MediaItem 实例。
     */
    private fun createImageItem(awemeId: String, index: Int, rawUrl: String): MediaItem {
        val url = UrlNormalizer.normalize(rawUrl)
        return MediaItem(
            type = MediaType.IMAGE,
            url = url,
            previewUrl = url,
            fileName = "douyin_${awemeId}_${index + 1}.jpg",
            referer = DOUYIN_REFERER,
        )
    }

    /**
     * 从 download_url_list 数组中提取 URL。
     *
     * @param list 输入：download_url_list JSON 数组。
     * @return 输出：图片 URL；未找到时返回 null。
     */
    private fun pickBestUrlFromDownloadList(list: JSONArray?): String? {
        if (list == null || list.length() == 0) return null
        for (index in 0 until list.length()) {
            when (val element = list.opt(index)) {
                is JSONObject -> {
                    pickBestImageUrl(element.optJSONArray("url_list"))?.let { return it }
                    element.optString("url").takeIf { it.isNotBlank() }?.let {
                        return UrlNormalizer.normalize(it)
                    }
                }
                is String -> if (element.isNotBlank()) return UrlNormalizer.normalize(element)
            }
        }
        return null
    }

    /**
     * 从 url_list 收集并规范化全部 URL。
     *
     * @param urlList 输入：URL 数组。
     * @return 输出：规范化后的 URL 列表。
     */
    private fun collectUrls(urlList: JSONArray?): List<String> {
        if (urlList == null || urlList.length() == 0) return emptyList()
        val urls = mutableListOf<String>()
        for (index in 0 until urlList.length()) {
            val element = urlList.opt(index)
            when (element) {
                is String -> if (element.isNotBlank()) urls.add(element)
                is JSONObject -> {
                    element.optString("url").takeIf { it.isNotBlank() }?.let { urls.add(it) }
                    element.optString("uri").takeIf { it.startsWith("http") }?.let { urls.add(it) }
                    element.optString("src").takeIf { it.startsWith("http") }?.let { urls.add(it) }
                }
                else -> urlList.optString(index).takeIf { it.isNotBlank() }?.let { urls.add(it) }
            }
        }
        return urls.map { UrlNormalizer.normalize(it) }
    }

    /**
     * 从 url_list 中选取图片 URL（不过滤 watermark）。
     *
     * @param urlList 输入：URL 数组。
     * @return 输出：图片 URL；列表为空时返回 null。
     */
    private fun pickBestImageUrl(urlList: JSONArray?): String? {
        return collectUrls(urlList).firstOrNull()
    }

    /**
     * 供单元测试调用：从 aweme JSON 构建媒体列表。
     *
     * @param item 输入：aweme JSON 对象。
     * @return 输出：媒体资源列表。
     */
    internal fun buildMediaItemsForTest(item: JSONObject): List<MediaItem> {
        return buildMediaItems(item)
    }

    /**
     * 供单元测试调用：提取视频变体列表。
     *
     * @param video 输入：video JSON 对象。
     * @return 输出：视频变体列表。
     */
    internal fun extractVideoVariantsForTest(video: JSONObject?): List<VideoVariant> {
        return extractVideoVariants(video)
    }

    /**
     * 抖音视频下载变体。
     *
     * @param url 输入：规范化后的下载 URL。
     * @param label 输入：变体标签。
     * @param sortOrder 输入：排序权重。
     * @param fileSuffix 输入：文件名后缀。
     * @return 输出：视频变体对象。
     */
    internal data class VideoVariant(
        val url: String,
        val label: String,
        val sortOrder: Int,
        val fileSuffix: String,
    )

    /**
     * 抖音作品内容类型。
     */
    internal enum class DouyinContentType {
        IMAGE,
        VIDEO,
    }

    /**
     * 供单元测试调用：判断作品内容类型。
     *
     * @param item 输入：aweme JSON 对象。
     * @return 输出：内容类型枚举。
     */
    internal fun classifyContentTypeForTest(item: JSONObject): DouyinContentType {
        val playUrl = extractVideoPlayUrl(item.optJSONObject("video"))
        return classifyContentType(item, playUrl)
    }

    /**
     * 判断 URL 是否明显为图片地址。
     *
     * @param url 输入：待检测 URL。
     * @return 输出：true 表示像图片地址。
     */
    private fun isImageUrl(url: String): Boolean {
        val lower = url.lowercase()
        return IMAGE_URL_HINTS.any { lower.contains(it) }
    }

    companion object {
        private const val DOUYIN_REFERER = "https://www.douyin.com/"
        private const val LABEL_CDN_CLEAN = "CDN 无水印"
        private const val LABEL_CDN_WM = "CDN 带水印"
        private const val LABEL_API_NOWM = "API 无水印"
        private const val LABEL_API_WM = "API 带水印"
        private const val LABEL_LOWBR = "低码率 CDN"
        private const val LABEL_H264 = "H264"
        private const val LABEL_H265 = "H265"
        private const val LABEL_DOWNLOAD = "下载地址"
        private const val SORT_CDN_CLEAN = 10
        private const val SORT_CDN_WM = 20
        private const val SORT_API_NOWM = 30
        private const val SORT_API_WM = 40
        private const val SORT_LOWBR = 50
        private const val SORT_H264 = 51
        private const val SORT_H265 = 52
        private const val SORT_DOWNLOAD = 60
        private const val SORT_BITRATE = 70
        private const val SORT_OTHER = 80
        private val PLAYWM_PATTERN = Regex("playwm", RegexOption.IGNORE_CASE)
        private val IMAGE_AWEME_TYPES = setOf(2, 68, 61)
        private val VIDEO_AWEME_TYPES = setOf(0, 4, 51, 107)
        private val IMAGE_URL_HINTS = listOf(".jpg", ".jpeg", ".webp", ".png", ".heic", "/image/")
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
