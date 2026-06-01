package com.taoguo.post_saver.parser

import com.taoguo.post_saver.model.MediaItem
import com.taoguo.post_saver.model.MediaType
import com.taoguo.post_saver.model.ParseResult
import com.taoguo.post_saver.model.Platform
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * XiaohongshuParser 单元测试。
 */
class XiaohongshuParserTest {

    private lateinit var parser: XiaohongshuParser

    /**
     * 初始化测试用解析器。
     */
    @Before
    fun setUp() {
        parser = XiaohongshuParser(XiaohongshuParser.defaultClient())
    }

    /**
     * discovery/item 分享链应能提取笔记 ID。
     */
    @Test
    fun extractNoteId_discoveryItemUrl_returnsNoteId() {
        val url =
            "https://www.xiaohongshu.com/discovery/item/69fdc0a60000000035023ceb" +
                "?xsec_token=CBclamcOLZW_0gPqqH3UzSHOYPWNkkqUaB-T6EUpktzqA%3D&xsec_source=app_share"
        assertEquals("69fdc0a60000000035023ceb", parser.extractNoteId(url))
    }

    /**
     * 分享链应保留 xsec_token 与 xsec_source 查询参数。
     */
    @Test
    fun buildNotePageUrl_discoveryItem_preservesSecurityParams() {
        val resolved =
            "https://www.xiaohongshu.com/discovery/item/69fdc0a60000000035023ceb" +
                "?xsec_token=token123&xsec_source=app_share&type=normal"
        val pageUrl = parser.buildNotePageUrlForTest("69fdc0a60000000035023ceb", resolved)
        assertTrue(pageUrl.contains("/discovery/item/69fdc0a60000000035023ceb"))
        assertTrue(pageUrl.contains("xsec_token=token123"))
        assertTrue(pageUrl.contains("xsec_source=app_share"))
    }

    /**
     * 图文笔记应从 imageList 提取 WB_DFT 高清图。
     */
    @Test
    fun mapNoteFromState_imageNote_returnsImages() {
        val state = loadFixture("xhs_image_note.json")
        val result = parser.mapNoteFromStateForTest(state, "69fdc0a60000000035023ceb")
        assertEquals(1, result.mediaItems.size)
        assertEquals(MediaType.IMAGE, result.mediaItems[0].type)
        assertTrue(result.mediaItems[0].url.contains("nc_n_webp_mw_1"))
        assertTrue(result.caption.contains("Ranch day"))
    }

    /**
     * 7 张图笔记去重后应仍为 7 条高清图。
     */
    @Test
    fun mapNoteFromState_carousel7_returnsSevenImages() {
        val state = loadFixture("xhs_carousel_7.json")
        val result = parser.mapNoteFromStateForTest(state, "69fdc0a60000000035023ceb")
        assertEquals(7, result.mediaItems.size)
        assertTrue(result.mediaItems.all { it.type == MediaType.IMAGE })
        assertTrue(result.mediaItems.all { it.url.contains("webp_mw") })
    }

    /**
     * 9 张图笔记应输出 9 条（含仅预览 URL 的图片位）。
     */
    @Test
    fun mapNoteFromState_carousel9_returnsNineImages() {
        val state = loadFixture("xhs_carousel_9.json")
        val result = parser.mapNoteFromStateForTest(state, "69fdc0a60000000035023ceb")
        assertEquals(9, result.mediaItems.size)
    }

    /**
     * 完全重复的 URL 应合并为一条。
     */
    @Test
    fun finalizeMediaItems_duplicateUrl_removed() {
        val noteId = "69fdc0a60000000035023ceb"
        val url = "https://sns-webpic-qc.xhscdn.com/202501180028/a001/spectrum/img001!nc_n_webp_mw_1"
        val items = listOf(
            MediaItem(type = MediaType.IMAGE, url = url, fileName = "xhs_${noteId}_1.jpg"),
            MediaItem(type = MediaType.IMAGE, url = url, fileName = "xhs_${noteId}_2.jpg"),
        )
        assertEquals(1, parser.finalizeMediaItemsForTest(items, noteId).size)
    }

    /**
     * HTML 与 INITIAL_STATE 合并时应保留全部不重复图片。
     */
    @Test
    fun mergeParseResults_combinesUniqueImagesFromBothSources() {
        val noteId = "69fdc0a60000000035023ceb"
        val state = parser.mapNoteFromStateForTest(loadFixture("xhs_carousel_7.json"), noteId)
        val htmlItems = (8..9).map { index ->
            MediaItem(
                type = MediaType.IMAGE,
                url = "https://sns-webpic-qc.xhscdn.com/202501180028/a$index/spectrum/img$index!nc_n_webp_mw_1",
                fileName = "tmp.jpg",
            )
        }
        val htmlResult = ParseResult(
            platform = Platform.XIAOHONGSHU,
            caption = "",
            mediaItems = htmlItems,
        )
        val merged = parser.mergeParseResultsForTest(state, htmlResult, noteId)
        assertEquals(9, merged.mediaItems.size)
        assertEquals("Ranch day", merged.caption)
    }

    /**
     * 仅含预览地址的图片位应输出高清下载 URL。
     */
    @Test
    fun mapNoteFromState_carousel9_previewSlotsUseHdUrl() {
        val state = loadFixture("xhs_carousel_9.json")
        val result = parser.mapNoteFromStateForTest(state, "69fdc0a60000000035023ceb")
        assertEquals(9, result.mediaItems.size)
        val previewOnly = result.mediaItems.takeLast(2)
        assertTrue(previewOnly.all { !it.url.contains("webp_prv") })
    }

    /**
     * HTML 兜底仅匹配 urlDefault，同图 urlPre 不应额外计入。
     */
    @Test
    fun parseImagesFromHtml_onlyUrlDefault_excludesUrlPre() {
        val noteId = "69fdc0a60000000035023ceb"
        val html = """
            noteId":"$noteId",
            "urlPre":"https://sns-webpic-qc.xhscdn.com/202501180028/e8da355f0f66b93801735d44d2403f93/spectrum/1040g0k031coeec8616005osp8qg9tcbj7tkmhh8!nc_n_webp_prv_1",
            "urlDefault":"https://sns-webpic-qc.xhscdn.com/202501180028/9a9667b8e0ea82be10f388b8c279d9d4/spectrum/1040g0k031coeec8616005osp8qg9tcbj7tkmhh8!nc_n_webp_mw_1"
        """.trimIndent()
        val result = parser.parseImagesFromHtmlForTest(html, noteId)
        assertEquals(1, result?.mediaItems?.size)
        assertTrue(result!!.mediaItems[0].url.contains("webp_mw"))
    }

    /**
     * 从 classpath 加载 JSON 测试夹具。
     *
     * @param name 输入：资源文件名。
     * @return 输出：JSONObject 根对象。
     */
    private fun loadFixture(name: String): JSONObject {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
            "Fixture not found: $name"
        }
        val json = stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        return JSONObject(json)
    }
}
