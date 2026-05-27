package com.taoguo.post_saver.model

/**
 * 单条可下载媒体资源。
 *
 * @param type 输入：媒体类型（图片或视频）。
 * @param url 输入：下载地址。
 * @param previewUrl 输入：预览图地址，可为空（视频封面或图片本身）。
 * @param fileName 输入：建议保存文件名，可为空。
 * @return 输出：媒体数据对象。
 */
data class MediaItem(
    val type: MediaType,
    val url: String,
    val previewUrl: String? = null,
    val fileName: String? = null,
)
