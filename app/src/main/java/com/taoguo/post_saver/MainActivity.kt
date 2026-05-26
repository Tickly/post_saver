package com.taoguo.post_saver

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * 作品下载助手主界面 Activity 入口。
 *
 * @param savedInstanceState 输入：Activity 重建时系统提供的状态（可为空）。
 * @return 输出：无返回值。
 */
class MainActivity : AppCompatActivity() {

    /**
     * Activity 创建回调，加载主布局。
     *
     * @param savedInstanceState 输入：Activity 重建时系统提供的状态（可为空）。
     * @return 输出：无返回值。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
