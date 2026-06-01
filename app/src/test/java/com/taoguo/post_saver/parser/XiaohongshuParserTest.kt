package com.taoguo.post_saver.parser

import com.taoguo.post_saver.model.MediaType
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
