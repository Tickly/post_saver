package com.taoguo.post_saver.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.taoguo.post_saver.R
import com.taoguo.post_saver.databinding.ItemMediaResultBinding
import com.taoguo.post_saver.model.MediaItem
import com.taoguo.post_saver.model.MediaType
import com.taoguo.post_saver.parser.UrlNormalizer

/**
 * 解析结果媒体列表适配器。
 *
 * @param onDownloadClick 输入：用户点击下载按钮时的回调，参数为媒体项与列表位置。
 */
class MediaResultAdapter(
    private val onDownloadClick: (MediaItem, Int) -> Unit,
) : RecyclerView.Adapter<MediaResultAdapter.MediaViewHolder>() {

    private val items = mutableListOf<MediaItem>()
    private val downloadingPositions = mutableSetOf<Int>()

    /**
     * 更新媒体列表数据。
     *
     * @param mediaItems 输入：新的媒体列表。
     * @return 输出：无返回值。
     */
    fun submitList(mediaItems: List<MediaItem>) {
        items.clear()
        items.addAll(mediaItems)
        downloadingPositions.clear()
        notifyDataSetChanged()
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
        notifyItemChanged(position)
    }

    /**
     * 创建 ViewHolder。
     *
     * @param parent 输入：父容器。
     * @param viewType 输入：视图类型。
     * @return 输出：MediaViewHolder 实例。
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemMediaResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return MediaViewHolder(binding)
    }

    /**
     * 绑定 ViewHolder 数据。
     *
     * @param holder 输入：ViewHolder。
     * @param position 输入：列表位置。
     * @return 输出：无返回值。
     */
    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    /**
     * 返回列表项数量。
     *
     * @return 输出：媒体项数量。
     */
    override fun getItemCount(): Int = items.size

    /**
     * 媒体列表 ViewHolder。
     */
    inner class MediaViewHolder(
        private val binding: ItemMediaResultBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        /**
         * 绑定单条媒体数据到视图。
         *
         * @param item 输入：媒体资源。
         * @param position 输入：列表位置。
         * @return 输出：无返回值。
         */
        fun bind(item: MediaItem, position: Int) {
            val context = binding.root.context
            val typeLabel = when (item.type) {
                MediaType.IMAGE -> context.getString(R.string.label_media_image)
                MediaType.VIDEO -> context.getString(R.string.label_media_video)
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
}
