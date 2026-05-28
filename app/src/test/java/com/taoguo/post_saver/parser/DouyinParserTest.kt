package com.taoguo.post_saver.parser

import com.taoguo.post_saver.model.MediaType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * DouyinParser 单元测试，验证图片/视频解析互不回归。
 */
class DouyinParserTest {

    private lateinit var parser: DouyinParser

    /**
     * 初始化测试用解析器。
     */
    @Before
    fun setUp() {
        parser = DouyinParser(DouyinParser.defaultClient())
    }

    /**
     * 视频作品应识别为 VIDEO 且提取无 watermark 播放地址。
     */
    @Test
    fun buildMediaItems_videoPost_returnsVideoWithoutWatermark() {
        val item = loadFixture("douyin_video.json")

        assertEquals(DouyinParser.DouyinContentType.VIDEO, parser.classifyContentTypeForTest(item))

        val mediaItems = parser.buildMediaItemsForTest(item)
        assertEquals(1, mediaItems.size)
        assertEquals(MediaType.VIDEO, mediaItems[0].type)
        assertTrue(mediaItems[0].url.contains("clean/video.mp4"))
        assertTrue(!mediaItems[0].url.contains("watermark", ignoreCase = true))
    }

    /**
     * 图文单图应识别为 IMAGE，优先 download_url_list。
     */
    @Test
    fun buildMediaItems_imagePost_prefersDownloadUrlList() {
        val item = loadFixture("douyin_image.json")

        assertEquals(DouyinParser.DouyinContentType.IMAGE, parser.classifyContentTypeForTest(item))

        val mediaItems = parser.buildMediaItemsForTest(item)
        assertEquals(1, mediaItems.size)
        assertEquals(MediaType.IMAGE, mediaItems[0].type)
        assertTrue(mediaItems[0].url.contains("good-watermark.jpeg"))
    }

    /**
     * 图集应识别为 IMAGE 且提取多条图片。
     */
    @Test
    fun buildMediaItems_carouselPost_returnsMultipleImages() {
        val item = loadFixture("douyin_carousel.json")

        assertEquals(DouyinParser.DouyinContentType.IMAGE, parser.classifyContentTypeForTest(item))

        val mediaItems = parser.buildMediaItemsForTest(item)
        assertEquals(2, mediaItems.size)
        assertTrue(mediaItems.all { it.type == MediaType.IMAGE })
        assertTrue(mediaItems[0].url.contains("image1.jpeg"))
        assertTrue(mediaItems[1].url.contains("image2.jpeg"))
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
