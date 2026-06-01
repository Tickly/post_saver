package com.taoguo.post_saver

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.taoguo.post_saver.databinding.ActivityMainBinding
import com.taoguo.post_saver.debug.ParseDebugStore
import com.taoguo.post_saver.download.MediaDownloader
import com.taoguo.post_saver.model.MediaItem
import com.taoguo.post_saver.model.ParseResult
import com.taoguo.post_saver.parser.ParseException
import com.taoguo.post_saver.parser.ParserRegistry
import com.taoguo.post_saver.parser.UnsupportedPlatformException
import com.taoguo.post_saver.ui.MediaResultListView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 作品下载助手主界面 Activity 入口。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mediaListView: MediaResultListView
    private lateinit var mediaDownloader: MediaDownloader
    private var currentMediaItems: List<MediaItem> = emptyList()
    private var isBatchDownloading = false

    /**
     * Activity 创建回调，初始化界面与事件。
     *
     * @param savedInstanceState 输入：Activity 重建时系统提供的状态（可为空）。
     * @return 输出：无返回值。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mediaDownloader = MediaDownloader(this)
        setupMediaListView()
        setupClickListeners()
    }

    /**
     * 初始化可下载资源平铺列表。
     *
     * @return 输出：无返回值。
     */
    private fun setupMediaListView() {
        mediaListView = MediaResultListView(
            container = binding.layoutMediaList,
            inflater = layoutInflater,
            onDownloadClick = { item, position -> downloadMediaItem(item, position) },
        )
    }

    /**
     * 绑定按钮点击事件。
     *
     * @return 输出：无返回值。
     */
    private fun setupClickListeners() {
        binding.buttonPaste.setOnClickListener { pasteFromClipboard() }
        binding.buttonParse.setOnClickListener { parseInputText() }
        binding.buttonDownloadAll.setOnClickListener { downloadAllMediaItems() }
        binding.buttonViewDebugJson.setOnClickListener { showDebugJsonDialog() }
    }

    /**
     * 弹出对话框展示最近一次解析的调试 JSON，并支持复制。
     *
     * @return 输出：无返回值。
     */
    private fun showDebugJsonDialog() {
        val json = ParseDebugStore.get()
        if (json.isNullOrBlank()) {
            Toast.makeText(this, R.string.msg_debug_json_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val textView = TextView(this).apply {
            text = json
            textSize = 11f
            setTextIsSelectable(true)
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val scrollView = ScrollView(this).apply {
            addView(textView)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_debug_json_title)
            .setView(scrollView)
            .setPositiveButton(R.string.btn_debug_json_copy) { _, _ ->
                copyDebugJsonToClipboard(json)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * 将调试 JSON 复制到系统剪贴板。
     *
     * @param json 输入：JSON 文本。
     * @return 输出：无返回值。
     */
    private fun copyDebugJsonToClipboard(json: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("parse_debug", json))
        Toast.makeText(this, R.string.msg_debug_json_copied, Toast.LENGTH_SHORT).show()
    }

    /**
     * 从剪贴板粘贴文本到输入框。
     *
     * @return 输出：无返回值。
     */
    private fun pasteFromClipboard() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        val clipText = clipboard.primaryClip?.let { clip ->
            if (clip.itemCount > 0) clip.getItemAt(0).coerceToText(this).toString() else ""
        }.orEmpty()

        if (clipText.isBlank()) {
            Toast.makeText(this, R.string.msg_clipboard_empty, Toast.LENGTH_SHORT).show()
            return
        }

        binding.editLink.setText(clipText)
        binding.editLink.setSelection(clipText.length)
        hideError()
        hideResult()
    }

    /**
     * 解析输入框中的分享文本。
     *
     * @return 输出：无返回值。
     */
    private fun parseInputText() {
        val text = binding.editLink.text?.toString().orEmpty()
        setParsingState(true)
        hideError()
        hideResult()

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { ParserRegistry.parseText(text) }
            }

            setParsingState(false)

            result.onSuccess { parseResult ->
                showParseResult(parseResult)
            }.onFailure { error ->
                showError(resolveErrorMessage(error))
            }
        }
    }

    /**
     * 下载单个媒体资源。
     *
     * @param item 输入：媒体资源。
     * @param position 输入：列表位置。
     * @return 输出：无返回值。
     */
    private fun downloadMediaItem(item: MediaItem, position: Int) {
        if (isBatchDownloading) return
        mediaListView.setDownloading(position, true)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { mediaDownloader.download(item) }
            }

            mediaListView.setDownloading(position, false)

            result.onSuccess { path ->
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.msg_download_success, path),
                    Toast.LENGTH_LONG,
                ).show()
            }.onFailure { error ->
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.msg_download_failed, error.message ?: "未知错误"),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    /**
     * 顺序下载当前解析结果中的全部媒体。
     *
     * @return 输出：无返回值。
     */
    private fun downloadAllMediaItems() {
        if (currentMediaItems.isEmpty()) {
            Toast.makeText(this, R.string.msg_download_all_empty, Toast.LENGTH_SHORT).show()
            return
        }
        if (isBatchDownloading) return

        setBatchDownloadState(true)
        lifecycleScope.launch {
            var successCount = 0
            var failedCount = 0
            for ((index, item) in currentMediaItems.withIndex()) {
                mediaListView.setDownloading(index, true)
                val result = withContext(Dispatchers.IO) {
                    runCatching { mediaDownloader.download(item) }
                }
                mediaListView.setDownloading(index, false)
                if (result.isSuccess) {
                    successCount++
                } else {
                    failedCount++
                }
            }
            setBatchDownloadState(false)
            Toast.makeText(
                this@MainActivity,
                getString(R.string.msg_download_all_summary, successCount, failedCount),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    /**
     * 展示解析成功的结果。
     *
     * @param result 输入：解析结果。
     * @return 输出：无返回值。
     */
    private fun showParseResult(result: ParseResult) {
        binding.layoutResult.visibility = View.VISIBLE
        binding.textCaption.text = result.caption.ifBlank { getString(R.string.welcome_message) }

        if (!result.author.isNullOrBlank()) {
            binding.textAuthor.visibility = View.VISIBLE
            binding.textAuthor.text = getString(R.string.label_author, result.author)
        } else {
            binding.textAuthor.visibility = View.GONE
        }

        currentMediaItems = result.mediaItems
        mediaListView.submitList(currentMediaItems)
        binding.buttonDownloadAll.visibility =
            if (currentMediaItems.isNotEmpty()) View.VISIBLE else View.GONE
        Toast.makeText(
            this,
            getString(R.string.msg_parse_success, currentMediaItems.size),
            Toast.LENGTH_SHORT,
        ).show()
    }

    /**
     * 展示错误信息。
     *
     * @param message 输入：错误文案。
     * @return 输出：无返回值。
     */
    private fun showError(message: String) {
        binding.textError.visibility = View.VISIBLE
        binding.textError.text = message
    }

    /**
     * 隐藏错误信息。
     *
     * @return 输出：无返回值。
     */
    private fun hideError() {
        binding.textError.visibility = View.GONE
        binding.textError.text = ""
    }

    /**
     * 隐藏解析结果区域。
     *
     * @return 输出：无返回值。
     */
    private fun hideResult() {
        binding.layoutResult.visibility = View.GONE
        binding.buttonDownloadAll.visibility = View.GONE
        currentMediaItems = emptyList()
        mediaListView.clear()
    }

    /**
     * 切换解析中的 UI 状态。
     *
     * @param parsing 输入：是否正在解析。
     * @return 输出：无返回值。
     */
    private fun setParsingState(parsing: Boolean) {
        binding.progressParsing.visibility = if (parsing) View.VISIBLE else View.GONE
        binding.buttonParse.isEnabled = !parsing && !isBatchDownloading
        binding.buttonPaste.isEnabled = !parsing && !isBatchDownloading
    }

    /**
     * 切换批量下载中的 UI 状态。
     *
     * @param downloading 输入：是否正在批量下载。
     * @return 输出：无返回值。
     */
    private fun setBatchDownloadState(downloading: Boolean) {
        isBatchDownloading = downloading
        binding.buttonDownloadAll.isEnabled = !downloading
        binding.buttonDownloadAll.text = getString(
            if (downloading) R.string.btn_download_all_running else R.string.btn_download_all,
        )
        binding.buttonParse.isEnabled = !downloading
        binding.buttonPaste.isEnabled = !downloading
    }

    /**
     * 将异常转换为用户可读的错误文案。
     *
     * @param error 输入：捕获的异常。
     * @return 输出：错误提示字符串。
     */
    private fun resolveErrorMessage(error: Throwable): String {
        return when (error) {
            is UnsupportedPlatformException -> error.message ?: getString(R.string.msg_unsupported_xhs)
            is ParseException -> error.message ?: getString(R.string.msg_parse_failed, "未知错误")
            else -> getString(R.string.msg_parse_failed, error.message ?: "未知错误")
        }
    }
}
