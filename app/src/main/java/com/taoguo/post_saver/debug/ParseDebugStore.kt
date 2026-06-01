package com.taoguo.post_saver.debug

/**
 * 保存最近一次解析产生的调试 JSON，供主界面「查看解析 JSON」展示与复制。
 */
object ParseDebugStore {

    private const val MAX_JSON_CHARS = 300_000

    @Volatile
    private var lastJson: String? = null

    /**
     * 写入调试 JSON 文本。
     *
     * @param json 输入：格式化后的 JSON 字符串。
     * @return 输出：无返回值。
     */
    fun set(json: String) {
        lastJson = if (json.length > MAX_JSON_CHARS) {
            json.take(MAX_JSON_CHARS) + "\n\n/* 已截断，仅保留前 $MAX_JSON_CHARS 字符 */"
        } else {
            json
        }
    }

    /**
     * 读取最近一次调试 JSON。
     *
     * @return 输出：JSON 文本；尚未解析时返回 null。
     */
    fun get(): String? = lastJson

    /**
     * 清空调试缓存。
     *
     * @return 输出：无返回值。
     */
    fun clear() {
        lastJson = null
    }
}
