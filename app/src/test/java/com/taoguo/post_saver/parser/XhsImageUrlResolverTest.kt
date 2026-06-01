package com.taoguo.post_saver.parser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * XhsImageUrlResolver 单元测试。
 */
class XhsImageUrlResolverTest {

    /**
     * 预览 URL 应能升级为高清后缀。
     */
    @Test
    fun upgradePreviewToHd_replacesPrvSuffix() {
        val preview =
            "https://sns-webpic-qc.xhscdn.com/202501180028/a008/spectrum/img008!nc_n_webp_prv_1"
        val upgraded = XhsImageUrlResolver.upgradePreviewToHd(preview)
        assertTrue(upgraded.contains("webp_mw"))
        assertFalse(XhsImageUrlResolver.isPreviewUrl(upgraded))
    }

    /**
     * 应能生成 ci 站下载候选地址。
     */
    @Test
    fun buildCiDownloadUrl_returnsCiHost() {
        val url =
            "https://sns-webpic-qc.xhscdn.com/202501180028/9a9667b8/spectrum/1040g0k031coeec8616005osp8qg9tcbj7tkmhh8!nc_n_webp_mw_1"
        val ci = XhsImageUrlResolver.buildCiDownloadUrl(url)
        assertTrue(ci != null && ci.contains("ci.xiaohongshu.com"))
    }

    /**
     * 同笔记不同图应得到不同 imageIdentityKey。
     */
    @Test
    fun imageIdentityKey_distinctFileIds_areUnique() {
        val url1 =
            "https://sns-webpic-qc.xhscdn.com/202606011137/a705d012/notes_pre_post/1040g3k031vtfvo2dis004a1b2t2hc4g7putpmjg!h5_1080jpg"
        val url2 =
            "https://sns-webpic-qc.xhscdn.com/202606011137/8a36e9fe/note_pre_post_uhdr/1040g3r831vtfji3v56og4a1b2t2hc4g7ft3trrg!h5_1080jpg"
        assertTrue(XhsImageUrlResolver.imageIdentityKey(url1) != XhsImageUrlResolver.imageIdentityKey(url2))
    }

    /**
     * h5_1080 应优先于 style 预览后缀。
     */
    @Test
    fun pickBestDownloadUrl_prefersH5DetailOverStylePreview() {
        val detail =
            "http://sns-webpic-qc.xhscdn.com/202606011137/a705d012/notes_pre_post/1040g3k031vtfvo2dis004a1b2t2hc4g7putpmjg!h5_1080jpg"
        val preview =
            "http://sns-webpic-qc.xhscdn.com/202606011137/6d209222/notes_pre_post/1040g3k031vtfvo2dis004a1b2t2hc4g7putpmjg!style_d4c824bab532bfe9"
        val best = XhsImageUrlResolver.pickBestDownloadUrl(listOf(preview, detail))
        assertTrue(best != null && best.contains("h5_1080"))
    }

    /**
     * 下载候选列表应包含升级后的 URL。
     */
    @Test
    fun getDownloadCandidates_includesUpgradedUrl() {
        val preview =
            "https://sns-webpic-qc.xhscdn.com/202501180028/a009/spectrum/img009!nc_n_webp_prv_1"
        val candidates = XhsImageUrlResolver.getDownloadCandidates(preview)
        assertTrue(candidates.any { it.contains("webp_mw") })
    }
}
