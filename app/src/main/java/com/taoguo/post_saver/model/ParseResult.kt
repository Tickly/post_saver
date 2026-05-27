package com.taoguo.post_saver.model

/**
 * 链接解析结果。
 *
 * @param platform 输入：来源平台。
 * @param caption 输入：作品文案。
 * @param author 输入：作者昵称，可为空。
 * @param mediaItems 输入：可下载的媒体列表。
 * @return 输出：解析结果对象。
 */
data class ParseResult(
    val platform: Platform,
    val caption: String,
    val author: String? = null,
    val mediaItems: List<MediaItem>,
)
