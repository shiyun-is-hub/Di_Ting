package com.diting.recorder.ui

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.HorizontalScrollView
import android.widget.LinearLayout

/**
 * 轻量双页滑动容器：容纳两个等宽子页，支持手势左右滑动切换。
 * 不依赖 ViewPager，避免引入额外依赖。
 */
class SwipePager(context: Context) : HorizontalScrollView(context) {

    private var pageWidth = 0
    private var currentPage = 0
    private var pageCount = 0

    /** 页切换回调列表（底栏高亮、顶栏显隐等各自注册，互不覆盖）。 */
    private val pageListeners = mutableListOf<(Int) -> Unit>()

    /** 注册页切换回调。 */
    fun addOnPageChanged(listener: (Int) -> Unit) {
        pageListeners.add(listener)
    }

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isFillViewport = true
    }

    /** 设置页（每个 View 按屏宽撑满）。 */
    fun setPages(pages: List<View>) {
        removeAllViews()
        val container = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        val screenW = resources.displayMetrics.widthPixels

        for (p in pages) {
            container.addView(
                p,
                LinearLayout.LayoutParams(
                    screenW,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        addView(
            container,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
        )
        pageCount = pages.size
        pageWidth = screenW

        post {
            // 兜底：以实际测量宽度为准
            if (width > 0) {
                pageWidth = width
                for (i in 0 until container.childCount) {
                    val lp = container.getChildAt(i).layoutParams
                    lp.width = pageWidth
                    container.getChildAt(i).layoutParams = lp
                }
            }
            val clp = container.layoutParams
            clp.width = pageWidth * pageCount
            container.layoutParams = clp
            snapToPage(currentPage, false)
        }
    }

    /** 外部（底栏点击）切页。 */
    fun goToPage(index: Int, smooth: Boolean = true) {
        if (index < 0 || index >= pageCount) return
        if (index == currentPage && smooth) {
            notifyPageChanged(index)
            return
        }
        currentPage = index
        snapToPage(index, smooth)
        notifyPageChanged(index)
    }

    private fun notifyPageChanged(index: Int) {
        for (l in pageListeners) l(index)
    }

    fun getCurrentPage(): Int = currentPage

    private fun snapToPage(index: Int, smooth: Boolean) {
        val target = pageWidth * index
        if (smooth) smoothScrollTo(target, 0) else scrollTo(target, 0)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // 禁止手动滑动切页：一律交给子 View 处理（保留纵向滚动），
        // 只允许通过 goToPage() 由底栏切换。
        return false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return false
    }
}