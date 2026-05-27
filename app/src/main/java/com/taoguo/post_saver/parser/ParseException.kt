package com.taoguo.post_saver.parser

/**
 * 链接解析失败异常。
 *
 * @param message 输入：面向用户的错误说明。
 * @return 输出：解析异常实例。
 */
class ParseException(message: String) : Exception(message)

/**
 * 平台暂不支持解析异常。
 *
 * @param message 输入：面向用户的错误说明。
 * @return 输出：不支持平台异常实例。
 */
class UnsupportedPlatformException(message: String) : Exception(message)
