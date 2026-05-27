package com.taoguo.post_saver.download

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.taoguo.post_saver.model.MediaItem
import com.taoguo.post_saver.model.MediaType
import com.taoguo.post_saver.parser.UrlNormalizer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 将解析出的媒体资源下载并保存到系统相册。
 */
class MediaDownloader(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
) {

    /**
     * 下载单个媒体文件到相册。
     *
     * @param item 输入：待下载的媒体资源。
     * @return 输出：保存后的相对路径描述（用于提示用户）。
     * @throws Exception 下载或写入失败时抛出。
     */
    fun download(item: MediaItem): String {
        val referer = item.referer ?: UrlNormalizer.refererFor(item.url)
        val downloadUrl = UrlNormalizer.normalize(item.url)
        val request = Request.Builder()
            .url(downloadUrl)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            )
            .header("Referer", referer)
            .build()

        val bytes = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("下载失败（HTTP ${response.code}）")
            }
            response.body?.bytes() ?: throw IllegalStateException("下载内容为空")
        }

        val fileName = item.fileName ?: defaultFileName(item)
        return when (item.type) {
            MediaType.IMAGE -> saveImage(bytes, fileName)
            MediaType.VIDEO -> saveVideo(bytes, fileName)
        }
    }

    /**
     * 保存图片到 Pictures/PostSaver 目录。
     *
     * @param bytes 输入：图片二进制数据。
     * @param fileName 输入：文件名。
     * @return 输出：保存路径描述。
     */
    private fun saveImage(bytes: ByteArray, fileName: String): String {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/PostSaver",
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            values,
        ) ?: throw IllegalStateException("无法创建图片文件")

        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(bytes)
        } ?: throw IllegalStateException("无法写入图片文件")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }

        return "Pictures/PostSaver/$fileName"
    }

    /**
     * 保存视频到 Movies/PostSaver 目录。
     *
     * @param bytes 输入：视频二进制数据。
     * @param fileName 输入：文件名。
     * @return 输出：保存路径描述。
     */
    private fun saveVideo(bytes: ByteArray, fileName: String): String {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MOVIES}/PostSaver",
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            values,
        ) ?: throw IllegalStateException("无法创建视频文件")

        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(bytes)
        } ?: throw IllegalStateException("无法写入视频文件")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        }

        return "Movies/PostSaver/$fileName"
    }

    /**
     * 生成默认文件名。
     *
     * @param item 输入：媒体资源。
     * @return 输出：默认文件名。
     */
    private fun defaultFileName(item: MediaItem): String {
        val suffix = System.currentTimeMillis()
        return when (item.type) {
            MediaType.IMAGE -> "post_saver_$suffix.jpg"
            MediaType.VIDEO -> "post_saver_$suffix.mp4"
        }
    }
}
