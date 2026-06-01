package com.taoguo.post_saver.debug

import com.taoguo.post_saver.model.MediaItem
import com.taoguo.post_saver.model.ParseResult
import com.taoguo.post_saver.model.Platform
import org.json.JSONArray
import org.json.JSONObject

/**
 * 将解析结果与调试字段组装为 JSON。
 */
object ParseDebugJson {

    private const val INITIAL_STATE_RAW_LIMIT = 120_000

    /**
     * 将 ParseResult 写入调试 JSON 根对象。
     *
     * @param root 输入：调试 JSON 根对象。
     * @param result 输入：解析结果。
     * @return 输出：无返回值。
     */
    fun putParseResult(root: JSONObject, result: ParseResult) {
        root.put("parseResult", toJsonObject(result))
    }

    /**
     * 将 ParseResult 转为 JSON 对象。
     *
     * @param result 输入：解析结果。
     * @return 输出：JSON 对象。
     */
    fun toJsonObject(result: ParseResult): JSONObject {
        val mediaItems = JSONArray()
        for (item in result.mediaItems) {
            mediaItems.put(toMediaItemJson(item))
        }
        return JSONObject().apply {
            put("platform", result.platform.name)
            put("caption", result.caption)
            put("author", result.author ?: JSONObject.NULL)
            put("mediaCount", result.mediaItems.size)
            put("mediaItems", mediaItems)
        }
    }

    /**
     * 将单条媒体转为 JSON 对象。
     *
     * @param item 输入：媒体项。
     * @return 输出：JSON 对象。
     */
    private fun toMediaItemJson(item: MediaItem): JSONObject {
        return JSONObject().apply {
            put("type", item.type.name)
            put("url", item.url)
            put("previewUrl", item.previewUrl ?: JSONObject.NULL)
            put("fileName", item.fileName ?: JSONObject.NULL)
            put("referer", item.referer ?: JSONObject.NULL)
        }
    }

    /**
     * 写入小红书页面调试字段。
     *
     * @param root 输入：调试 JSON 根对象。
     * @param pageUrl 输入：笔记页 URL。
     * @param noteId 输入：笔记 ID。
     * @param parseSource 输入：解析来源说明。
     * @param note 输入：笔记 JSON；可为空。
     * @param initialStateRaw 输入：原始 INITIAL_STATE 文本；可为空。
     * @param htmlImageUrls 输入：HTML 兜底图片 URL 列表。
     * @return 输出：无返回值。
     */
    fun putXhsFields(
        root: JSONObject,
        pageUrl: String,
        noteId: String,
        parseSource: String,
        note: JSONObject?,
        initialStateRaw: String?,
        htmlImageUrls: List<String>,
    ) {
        root.put("platform", Platform.XIAOHONGSHU.name)
        root.put("pageUrl", pageUrl)
        root.put("noteId", noteId)
        root.put("parseSource", parseSource)
        if (note != null) {
            root.put("note", note)
            val imageList = note.optJSONArray("imageList")
                ?: note.optJSONArray("image_list")
                ?: note.optJSONArray("images")
            root.put("imageListCount", imageList?.length() ?: 0)
        } else {
            root.put("note", JSONObject.NULL)
            root.put("imageListCount", 0)
        }
        if (!initialStateRaw.isNullOrBlank()) {
            val truncated = initialStateRaw.length > INITIAL_STATE_RAW_LIMIT
            root.put("initialStateRawTruncated", truncated)
            root.put(
                "initialStateRaw",
                if (truncated) {
                    initialStateRaw.take(INITIAL_STATE_RAW_LIMIT)
                } else {
                    initialStateRaw
                },
            )
            runCatching {
                val sanitized = sanitizeJsJson(initialStateRaw)
                root.put("initialState", JSONObject(sanitized))
            }.onFailure { error ->
                root.put("initialStateParseError", error.message ?: "unknown")
            }
        }
        val urls = JSONArray()
        for (url in htmlImageUrls) {
            urls.put(url)
        }
        root.put("htmlImageUrls", urls)
        root.put("htmlImageUrlCount", htmlImageUrls.size)
    }

    /**
     * 写入抖音 aweme 调试字段。
     *
     * @param root 输入：调试 JSON 根对象。
     * @param itemId 输入：作品 ID。
     * @param resolvedUrl 输入：解析后的链接。
     * @param aweme 输入：aweme JSON；可为空。
     * @param parseSource 输入：解析来源。
     * @return 输出：无返回值。
     */
    fun putDouyinFields(
        root: JSONObject,
        itemId: String,
        resolvedUrl: String,
        aweme: JSONObject?,
        parseSource: String,
    ) {
        root.put("platform", Platform.DOUYIN.name)
        root.put("itemId", itemId)
        root.put("resolvedUrl", resolvedUrl)
        root.put("parseSource", parseSource)
        root.put("aweme", aweme ?: JSONObject.NULL)
    }

    /**
     * 粗略清理 JS 对象文本以便 JSONObject 解析。
     *
     * @param raw 输入：原始文本。
     * @return 输出：可尝试解析的 JSON 文本。
     */
    private fun sanitizeJsJson(raw: String): String {
        return raw
            .trim()
            .trimEnd(';')
            .replace(":undefined", ":null")
            .replace(",undefined", ",null")
            .replace("void 0", "null")
    }
}
