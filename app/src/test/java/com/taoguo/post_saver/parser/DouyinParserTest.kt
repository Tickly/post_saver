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
     * 视频作品应识别为 VIDEO 且提取 clean/watermark 两条 CDN 变体。
     */
    @Test
    fun buildMediaItems_videoPost_returnsCdnVariants() {
        val item = loadFixture("douyin_video.json")

        assertEquals(DouyinParser.DouyinContentType.VIDEO, parser.classifyContentTypeForTest(item))

        val mediaItems = parser.buildMediaItemsForTest(item)
        assertEquals(2, mediaItems.size)
        assertTrue(mediaItems.all { it.type == MediaType.VIDEO })
        assertTrue(mediaItems.any { it.url.contains("clean/video.mp4") })
        assertTrue(mediaItems.any { it.url.contains("watermark/video.mp4") })
        assertTrue(mediaItems.any { it.label == "CDN 无水印" })
        assertTrue(mediaItems.any { it.label == "CDN 带水印" })
    }

    /**
     * playwm API 地址应同时产出 API 无水印与 API 带水印两条变体。
     */
    @Test
    fun buildMediaItems_videoPost_playwmUrl_returnsApiVariants() {
        val item = loadFixture("douyin_video_playwm.json")

        assertEquals(DouyinParser.DouyinContentType.VIDEO, parser.classifyContentTypeForTest(item))

        val mediaItems = parser.buildMediaItemsForTest(item)
        assertEquals(2, mediaItems.size)
        assertTrue(mediaItems.all { it.type == MediaType.VIDEO })
        assertTrue(mediaItems.any { it.url.contains("/aweme/v1/play/") && !it.url.contains("playwm") })
        assertTrue(mediaItems.any { it.url.contains("playwm") })
        assertTrue(mediaItems.any { it.label == "API 无水印" })
        assertTrue(mediaItems.any { it.label == "API 带水印" })
    }

    /**
     * url_list 为空时应由 play_addr.uri 构造 play 与 playwm 两条变体。
     */
    @Test
    fun buildMediaItems_videoPost_uriOnly_buildsPlayAndPlaywmVariants() {
        val item = loadFixture("douyin_video_uri_only.json")

        val mediaItems = parser.buildMediaItemsForTest(item)
        assertEquals(2, mediaItems.size)
        assertTrue(mediaItems.all { it.type == MediaType.VIDEO })
        assertTrue(mediaItems.any { it.url.contains("/aweme/v1/play/") && !it.url.contains("playwm") })
        assertTrue(mediaItems.any { it.url.contains("playwm") })
        assertTrue(mediaItems.any { it.url.contains("v0400abc0000testvideouri01") })
    }

    /**
     * embeddedHtml 样本应产出 API 变体，带水印链保留原 playwm 路径。
     */
    @Test
    fun buildMediaItems_embeddedHtmlSample_returnsApiVariantsWithPlaywmFallback() {
        val item = loadFixture("douyin_video_embedded_html.json")

        assertEquals(DouyinParser.DouyinContentType.VIDEO, parser.classifyContentTypeForTest(item))

        val mediaItems = parser.buildMediaItemsForTest(item)
        assertTrue(mediaItems.size >= 2)
        assertTrue(mediaItems.any { it.label == "API 无水印" && it.url.contains("/aweme/v1/play/") })
        assertTrue(mediaItems.any { it.label == "API 带水印" && it.url.contains("playwm") })
        assertTrue(mediaItems.any { it.fileName?.contains("api_wm") == true })
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
