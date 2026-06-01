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
