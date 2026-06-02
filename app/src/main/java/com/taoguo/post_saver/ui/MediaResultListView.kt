package com.taoguo.post_saver.ui

import android.view.LayoutInflater
import android.widget.LinearLayout
import coil.load
import com.taoguo.post_saver.R
import com.taoguo.post_saver.databinding.ItemMediaResultBinding
import com.taoguo.post_saver.model.MediaItem
import com.taoguo.post_saver.model.MediaType
import com.taoguo.post_saver.parser.UrlNormalizer

/**
 * 在 LinearLayout 中平铺展示可下载资源（无 RecyclerView，随页面整体滚动）。
 */
class MediaResultListView(
    private val container: LinearLayout,
    private val inflater: LayoutInflater,
    private val onDownloadClick: (MediaItem, Int) -> Unit,
) {

    private val items = mutableListOf<MediaItem>()
    private val rowBindings = mutableListOf<ItemMediaResultBinding>()
    private val downloadingPositions = mutableSetOf<Int>()

    /**
     * 更新媒体列表并重建子视图。
     *
     * @param mediaItems 输入：新的媒体列表。
     * @return 输出：无返回值。
     */
    fun submitList(mediaItems: List<MediaItem>) {
        items.clear()
        items.addAll(mediaItems)
        downloadingPositions.clear()
        container.removeAllViews()
        rowBindings.clear()
        for ((index, item) in items.withIndex()) {
            val binding = ItemMediaResultBinding.inflate(inflater, container, true)
            rowBindings.add(binding)
            bindRow(binding, item, index)
        }
    }

    /**
     * 清空列表。
     *
     * @return 输出：无返回值。
     */
    fun clear() {
        submitList(emptyList())
    }

    /**
     * 设置指定位置的下载状态。
     *
     * @param position 输入：列表位置。
     * @param downloading 输入：是否正在下载。
     * @return 输出：无返回值。
     */
    fun setDownloading(position: Int, downloading: Boolean) {
        if (downloading) {
            downloadingPositions.add(position)
        } else {
            downloadingPositions.remove(position)
        }
        rowBindings.getOrNull(position)?.let { binding ->
            val item = items.getOrNull(position) ?: return
            bindRow(binding, item, position)
        }
    }

    /**
     * 绑定单条媒体行。
     *
     * @param binding 输入：行视图绑定。
     * @param item 输入：媒体资源。
     * @param position 输入：位置索引。
     * @return 输出：无返回值。
     */
    private fun bindRow(binding: ItemMediaResultBinding, item: MediaItem, position: Int) {
        val context = binding.root.context
        val typeLabel = when (item.type) {
            MediaType.IMAGE -> context.getString(R.string.label_media_image)
            MediaType.VIDEO -> {
                if (!item.label.isNullOrBlank()) {
                    context.getString(R.string.label_media_video_variant, item.label)
                } else {
                    context.getString(R.string.label_media_video)
                }
            }
        }
        binding.textMediaType.text = typeLabel
        binding.textMediaIndex.text = context.getString(R.string.label_media_index, position + 1)

        val previewUrl = UrlNormalizer.normalize(item.previewUrl ?: item.url)
        val referer = item.referer ?: UrlNormalizer.refererFor(previewUrl)
        binding.imagePreview.load(previewUrl) {
            crossfade(true)
            addHeader("Referer", referer)
            addHeader(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            )
            placeholder(R.drawable.ic_launcher_foreground)
            error(R.drawable.ic_launcher_foreground)
        }

        val isDownloading = downloadingPositions.contains(position)
        binding.buttonDownload.isEnabled = !isDownloading
        binding.buttonDownload.text = if (isDownloading) {
            context.getString(R.string.btn_downloading)
        } else {
            context.getString(R.string.btn_download)
        }
        binding.buttonDownload.setOnClickListener {
            onDownloadClick(item, position)
        }
    }
}
