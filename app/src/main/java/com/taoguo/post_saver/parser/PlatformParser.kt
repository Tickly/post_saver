package com.taoguo.post_saver.parser

import com.taoguo.post_saver.model.ParseResult

/**
 * 平台链接解析器接口。
 */
interface PlatformParser {

    /**
     * 解析分享链接，提取文案与媒体资源。
     *
     * @param url 输入：平台分享链接。
     * @return 输出：解析结果。
     * @throws ParseException 解析失败时抛出。
     */
    fun parse(url: String): ParseResult
}
