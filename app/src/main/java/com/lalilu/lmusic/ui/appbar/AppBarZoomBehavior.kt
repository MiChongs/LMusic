package com.lalilu.lmusic.ui.appbar

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce.DAMPING_RATIO_NO_BOUNCY
import androidx.dynamicanimation.animation.SpringForce.STIFFNESS_LOW
import com.dirror.lyricviewx.LyricViewX
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.lalilu.R
import com.lalilu.lmusic.ui.drawee.PaletteDraweeView
import com.lalilu.lmusic.utils.DeviceUtil
import com.lalilu.lmusic.utils.HapticUtils
import com.lalilu.lmusic.utils.Mathf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.qinc.lib.edgetranslucent.EdgeTransparentView
import kotlin.coroutines.CoroutineContext

class AppBarZoomBehavior(
    private val helper: AppBarStatusHelper,
    context: Context,
    attrs: AttributeSet? = null
) : AppBarLayout.Behavior(context, attrs), CoroutineScope {
    override val coroutineContext: CoroutineContext = Dispatchers.IO

    private var mToolbar: Toolbar? = null
    private var mLyricViewX: LyricViewX? = null
    private var nestedChildView: ViewGroup? = null
    private var mDraweeView: PaletteDraweeView? = null
    private var mSpringAnimation: SpringAnimation? = null
    private var mEdgeTransparentView: EdgeTransparentView? = null
    private var mCollapsingToolbarLayout: CollapsingToolbarLayout? = null
    private var interpolator = AccelerateDecelerateInterpolator()

    private val gestureDetector =
        GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent?): Boolean = false

            override fun onDown(e: MotionEvent?): Boolean {
                nestedChildView?.startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL)
                return true
            }

            override fun onScroll(
                e1: MotionEvent?, e2: MotionEvent?,
                distanceX: Float, distanceY: Float
            ): Boolean {
                nestedChildView?.let { nestedChildScrollBy(it, distanceY.toInt()) }
                return true
            }

            override fun onFling(
                e1: MotionEvent?, e2: MotionEvent?,
                velocityX: Float, velocityY: Float
            ): Boolean {
                nestedChildView?.stopNestedScroll()
                return true
            }
        })

    /**
     *  记录 AppBar 区域上手指的滑动，并传递给 Appbar 的 child 使其模拟嵌套滑动
     */
    override fun onTouchEvent(
        parent: CoordinatorLayout, child: AppBarLayout, ev: MotionEvent
    ): Boolean {
        return gestureDetector.onTouchEvent(ev).also {
            if (ev.action == MotionEvent.ACTION_UP)
                nestedChildView?.stopNestedScroll()
        }
    }

    /**
     *  在布局子控件时进行初始化
     */
    override fun onLayoutChild(
        parent: CoordinatorLayout,
        abl: AppBarLayout,
        layoutDirection: Int
    ): Boolean {
        initialize(parent, abl)
        return super.onLayoutChild(parent, abl, layoutDirection)
    }

    /**
     *  初始化各个子控件,获取基础的长宽变量
     */
    private fun initialize(parent: CoordinatorLayout, appBarLayout: AppBarLayout) {
        appBarLayout.clipChildren = false
        nestedChildView = parent.getChildAt(1) as ViewGroup
        mToolbar = appBarLayout.findViewById(R.id.fm_toolbar)
        mDraweeView = appBarLayout.findViewById(R.id.fm_top_pic)
        mLyricViewX = appBarLayout.findViewById(R.id.fm_lyric_view_x)
        mEdgeTransparentView = appBarLayout.findViewById(R.id.fm_edge_transparent_view)
        mCollapsingToolbarLayout = appBarLayout.findViewById(R.id.fm_collapse_layout)

        helper.also { hp ->
            hp.initialize(appBarLayout) { percent ->
                mDraweeView?.let { it.alpha = percent }
            }
            hp.deviceHeight = DeviceUtil.getHeight(appBarLayout.context)
            hp.normalHeight = mDraweeView?.height ?: -1
            hp.maxExpandHeight = hp.deviceHeight - hp.normalHeight
        }

        // mLyricViewX 从小窗打开后高度错误问题，不懂为什么需要协程才可以成功设置高度
        setHeightToView(mLyricViewX, helper.deviceHeight - 100)
        setHeightToView(mEdgeTransparentView, helper.deviceHeight - 100)

        if (helper.currentState == STATE_FULLY_EXPENDED) {
            resizeChild(appBarLayout, helper.deviceHeight)
        }
    }

    private fun setHeightToView(view: View?, height: Number) {
        view?.let {
            if (it.height == height.toInt()) return
            launch(Dispatchers.Main) {
                it.layoutParams = it.layoutParams.also { params -> params.height = height.toInt() }
            }
        }
    }

    /**
     *  拖动子控件,嵌套滚动发生时的周期函数
     */
    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout, child: AppBarLayout,
        target: View, dx: Int, dy: Int, consumed: IntArray, type: Int
    ) {
        // 计算此事件中 appbar 的 bottom 应该到达的下一个位置
        var nextPosition = child.bottom - dy
        val offsetPosition = child.bottom - helper.normalHeight

        // 获取现在所处位置的状态 Status，决定是否需要使 Appbar 拉伸
        when (helper.getNextStatusByNextPosition(nextPosition)) {
            STATE_EXPENDED,
            STATE_FULLY_EXPENDED -> {
                if (dy < 0) {
                    // 根据所在位置的百分比为滑动添加阻尼
                    // 经过阻尼衰减得到正确的 nextPosition
                    val percent = 1 - offsetPosition / helper.maxDragHeight.toFloat()
                    if (percent in 0F..1F) nextPosition = child.bottom - (dy * percent).toInt()
                }

                // 通过 nextPosition 重新定位
                resizeChild(child, nextPosition)
                // 判断重新定位后是否需要转换状态
                checkState(child, child.bottom)

                mSpringAnimation?.cancel()

                val isNeedConsume = nextPosition in helper.normalHeight..helper.deviceHeight
                consumed[1] = if (isNeedConsume) Int.MAX_VALUE else 0

                if (child.bottom > helper.normalHeight) return
            }
        }

        super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type)
    }


    /**
     *  重新定位各个控件的大小和位置
     */
    private fun resizeChild(abl: AppBarLayout, nextPosition: Int) {
        if (nextPosition <= 0 || helper.normalHeight <= 0) return

        // 限定 appbar 的高度在指定范围内
        abl.bottom = Mathf.clamp(helper.normalHeight, helper.deviceHeight, nextPosition)

        val offsetPosition = abl.bottom - helper.normalHeight
        val scaleValue = Mathf.clamp(0F, 5F, abl.bottom / helper.normalHeight.toFloat())
        val animatePercent = Mathf.clamp(0F, 1F, offsetPosition / helper.maxExpandHeight.toFloat())

        mDraweeView?.let {
            it.blurBg(animatePercent)
            it.scaleX = scaleValue
            it.scaleY = scaleValue
            it.translationY = offsetPosition / 2F
        }

        val interpolation = interpolator.getInterpolation(animatePercent)
        val reverseValue = if (animatePercent in 0F..0.5F) animatePercent else 1F - animatePercent
        val alphaPercentDecrease = (1F - interpolation * 2).coerceAtLeast(0F)
        val alphaPercentIncrease = (2 * interpolation - 1F).coerceAtLeast(0F)

        mCollapsingToolbarLayout?.let {
            val toolbarTextColor = Color.argb((alphaPercentDecrease * 255).toInt(), 255, 255, 255)
            it.top = (helper.normalHeight / 2 * reverseValue).toInt()
            it.bottom = (helper.normalHeight + helper.maxExpandHeight * animatePercent).toInt()
            it.setExpandedTitleColor(toolbarTextColor)
        }

        mToolbar?.let {
            it.visibility = if (alphaPercentDecrease <= 0.05) View.INVISIBLE else View.VISIBLE
            it.alpha = alphaPercentDecrease
        }

        mLyricViewX?.let {
            it.alpha = alphaPercentIncrease
        }
    }

    @Volatile
    private var isAnimationEnd = true

    private fun checkState(child: AppBarLayout, bottom: Int) {
        val offsetPosition = bottom - helper.normalHeight
        val tinyMachine = helper.tinyMachine

        val topSize = helper.maxDragHeight * 0.6f
        val bottomSide = helper.maxExpandHeight - topSize

        if (isAnimationEnd) {
            when (helper.currentState) {
                STATE_EXPENDED -> {
                    if (offsetPosition <= topSize) return
                    isAnimationEnd = false
                    tinyMachine.fireEvent(EVENT_EXPEND)
                    HapticUtils.haptic(child)
                }
                STATE_FULLY_EXPENDED -> {
                    if (offsetPosition >= bottomSide) return
                    isAnimationEnd = false
                    tinyMachine.fireEvent(EVENT_COLLAPSE)
                    HapticUtils.haptic(child)
                }
            }
        } else {
            when (helper.currentState) {
                STATE_FULLY_EXPENDED -> {
                    if (offsetPosition > topSize) return
                    isAnimationEnd = true
                    tinyMachine.fireEvent(EVENT_COLLAPSE)
                    HapticUtils.weakHaptic(child)
                }
                STATE_EXPENDED -> {
                    if (offsetPosition < bottomSide) return
                    isAnimationEnd = true
                    tinyMachine.fireEvent(EVENT_EXPEND)
                    HapticUtils.weakHaptic(child)
                }
            }
        }
    }

    /**
     *  嵌套滚动结束时,根据就近边恢复位置
     */
    override fun onStopNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        abl: AppBarLayout,
        target: View,
        type: Int
    ) {
        if (abl.bottom > helper.normalHeight) {
            var toPosition = helper.normalHeight
            if (helper.currentState == STATE_FULLY_EXPENDED)
                toPosition = helper.deviceHeight
            recoveryToPosition(abl, toPosition)
        }
        super.onStopNestedScroll(coordinatorLayout, abl, target, type)
    }

    /**
     *  回到任意位置的方法
     */
    private fun recoveryToPosition(abl: AppBarLayout, position: Number) {
        if (mSpringAnimation == null) {
            mSpringAnimation = SpringAnimation(
                abl, appBarLayoutFloatProperty,
                helper.normalHeight.toFloat()
            ).apply {
                this.spring.dampingRatio = DAMPING_RATIO_NO_BOUNCY
                this.spring.stiffness = STIFFNESS_LOW
                this.addEndListener { _, _, _, _ ->
                    isAnimationEnd = true
                }
            }
        }
        mSpringAnimation?.cancel()
        mSpringAnimation?.animateToFinalPosition(position.toFloat())
    }

    private val appBarLayoutFloatProperty =
        object : FloatPropertyCompat<AppBarLayout>("appbar_bottom") {
            override fun setValue(`object`: AppBarLayout?, value: Float) {
                `object`?.let {
                    resizeChild(`object`, value.toInt())
                }
            }

            override fun getValue(`object`: AppBarLayout?): Float {
                return `object`?.bottom?.toFloat() ?: 0f
            }
        }

    /**
     *  模拟子控件嵌套滚动
     */
    private fun nestedChildScrollBy(nestedChildView: ViewGroup, dy: Int) {
        nestedChildView.dispatchNestedPreScroll(0, dy, null, null)
        nestedChildView.dispatchNestedScroll(0, 0, 0, dy, null)
    }
}