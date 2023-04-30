package com.lalilu.ui.appbar

import android.content.Context
import android.util.AttributeSet
import android.view.*
import android.widget.OverScroller
import androidx.annotation.FloatRange
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.util.ObjectsCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.lalilu.ui.internal.StateHelper
import com.lalilu.ui.internal.StateHelper.Companion.STATE_COLLAPSED
import com.lalilu.ui.internal.StateHelper.Companion.STATE_EXPENDED
import com.lalilu.ui.internal.StateHelper.Companion.STATE_FULLY_EXPENDED
import com.lalilu.ui.internal.StateHelper.Companion.STATE_MIDDLE
import com.lalilu.ui.internal.StateHelper.Companion.STATE_NORMAL
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.roundToInt


const val INVALID_POINTER = -1

abstract class ExpendHeaderBehavior<V : AppbarLayout>(
    private val mContext: Context?, attrs: AttributeSet?
) : ViewOffsetExpendBehavior<V>(mContext, attrs), AntiMisTouchEvent, StateHelper.Adapter {
    override var stateHelper: StateHelper = StateHelper()

    open val interceptSize: Int = 64
    private var mScrollAnimation: SpringAnimation? = null
    private var scroller: OverScroller? = null
    private var velocityTracker: VelocityTracker? = null
    private var lastInsets: WindowInsetsCompat? = null

    private var isBeingDragged = false
    private var activePointerId = INVALID_POINTER
    private var lastMotionY = 0
    private var touchSlop = -1

    protected var parentWeakReference: WeakReference<CoordinatorLayout>? = null
    protected var childWeakReference: WeakReference<V>? = null

    override fun isInPlaceToIntercept(rawY: Float): Boolean {
        childWeakReference?.get()?.let {
            return rawY >= (it.height - interceptSize) && rawY <= it.height
        }
        return false
    }

    override fun isTimeToIntercept(): Boolean {
        return topAndBottomOffset >= getFullyExpendOffset() - interceptSize
    }

    private fun getTopInset(): Int {
        return lastInsets?.getInsets(WindowInsetsCompat.Type.systemBars())?.top ?: 0
    }

    open fun canDragView(view: V): Boolean {
        return false
    }

    open fun getMaxDragOffset(): Float {
        return 200f
    }

    @FloatRange(from = 0.0, to = 1.0)
    open fun getMaxDragThreshold(): Float {
        return 0.6f
    }

    open fun getCollapsedOffset(
        parent: View? = parentWeakReference?.get(),
        child: V? = childWeakReference?.get()
    ): Int {
        return -(child?.totalScrollRange ?: 0)
    }

    open fun getFullyExpendOffset(
        parent: View? = parentWeakReference?.get(),
        child: V? = childWeakReference?.get()
    ): Int {
        return if (parent != null) parent.measuredHeight - parent.measuredWidth else 0
    }

    override fun setTopAndBottomOffset(offset: Int): Boolean {
        return super.setTopAndBottomOffset(offset).also {
            val min = getCollapsedOffset()
            val dragOffset = (getMaxDragOffset() * getMaxDragThreshold()).toInt()
            val max = getFullyExpendOffset()

            stateHelper.nowState = when (topAndBottomOffset) {
                in min until (min + dragOffset) -> STATE_COLLAPSED
                in (min + dragOffset) until -dragOffset -> STATE_NORMAL
                in -dragOffset until dragOffset -> STATE_EXPENDED
                in dragOffset until (max - dragOffset) -> STATE_MIDDLE
                else -> STATE_FULLY_EXPENDED
            }
        }
    }

    open fun setHeaderTopBottomOffset(
        newOffset: Int,
        minOffset: Int = getCollapsedOffset(),
        maxOffset: Int = getFullyExpendOffset()
    ): Int {
        var consumed = 0
        val curOffset = topAndBottomOffset
        if (minOffset != 0 && curOffset in minOffset..maxOffset) {
            // If we have some scrolling range, and we're currently within the min and max
            // offsets, calculate a new offset
            val offset = newOffset.coerceIn(minOffset, maxOffset)
            if (curOffset != offset) {
                setTopAndBottomOffset(offset)
                // Update how much dy we have consumed
                consumed = curOffset - offset
            }
        }
        return consumed
    }


    override fun onInterceptTouchEvent(
        parent: CoordinatorLayout,
        child: V,
        ev: MotionEvent
    ): Boolean {
        if (touchSlop < 0) {
            touchSlop = ViewConfiguration.get(parent.context).scaledTouchSlop
        }

        // Shortcut since we're being dragged
        if (ev.actionMasked == MotionEvent.ACTION_MOVE && isBeingDragged) {
            if (activePointerId == INVALID_POINTER) {
                // If we don't have a valid id, the touch down wasn't on content.
                return false
            }
            val pointerIndex = ev.findPointerIndex(activePointerId)
            if (pointerIndex == -1) {
                return false
            }
            val y = ev.getY(pointerIndex).toInt()
            val yDiff = abs(y - lastMotionY)
            if (yDiff > touchSlop) {
                lastMotionY = y
                return true
            }
        }

        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            activePointerId = INVALID_POINTER
            val x = ev.x.toInt()
            val y = ev.y.toInt()
            isBeingDragged = parent.isPointInChildBounds(child, x, y) && !ignoreTouchEvent(ev)
            if (isBeingDragged) {
                lastMotionY = y
                activePointerId = ev.getPointerId(0)
                velocityTracker = velocityTracker ?: VelocityTracker.obtain()

                // There is an animation in progress. Stop it and catch the view.
                mScrollAnimation?.let {
                    if (it.isRunning) it.cancel()
                }
            }
        }
        velocityTracker?.addMovement(ev)
        return false
    }

    override fun onTouchEvent(
        parent: CoordinatorLayout, child: V, ev: MotionEvent
    ): Boolean {

        var consumeUp = false
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val activePointerIndex = ev.findPointerIndex(activePointerId)
                if (activePointerIndex == -1) return false
                val y = ev.getY(activePointerIndex).toInt()
                val dy = lastMotionY - y
                lastMotionY = y
                // We're being dragged so scroll the ABL
                scroll(dy)
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val newIndex = if (ev.actionIndex == 0) 1 else 0
                activePointerId = ev.getPointerId(newIndex)
                lastMotionY = (ev.getY(newIndex) + 0.5f).toInt()
            }

            MotionEvent.ACTION_UP -> {
                isBeingDragged = false
                activePointerId = INVALID_POINTER
                velocityTracker?.let {
                    consumeUp = true
                    it.addMovement(ev)
                    it.computeCurrentVelocity(1000)
                    val velocityY = it.getYVelocity(activePointerId)
                    fling(velocityY)
                    it.recycle()
                    velocityTracker = null
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                isBeingDragged = false
                activePointerId = INVALID_POINTER
                velocityTracker?.let {
                    it.recycle()
                    velocityTracker = null
                }
            }
        }
        velocityTracker?.addMovement(ev)
        return isBeingDragged || consumeUp
    }

    /**
     * 在此获取 [lastInsets]
     * 用于获取状态栏高度等等用途
     */
    override fun onApplyWindowInsets(
        parent: CoordinatorLayout,
        child: V,
        insets: WindowInsetsCompat
    ): WindowInsetsCompat {
        if (ViewCompat.getFitsSystemWindows(child) &&
            !ObjectsCompat.equals(lastInsets, insets)
        ) {
            lastInsets = insets
        }
        return insets
    }

    fun shouldSkipExpandedState(appbar: V?): Boolean {
        return appbar?.let { !canDragView(appbar) } ?: false
    }

    /**
     * 根据当前状态 [nowState] 和前一个状态 [lastState] 判断应该贴合的目标边offset
     * 并调用 [animateOffsetTo] 贴合至指定的边
     *
     */
    fun snapToChildIfNeeded() {
        if (shouldSkipExpandedState(childWeakReference?.get())) {
            when (stateHelper.nowState) {
                STATE_COLLAPSED -> getCollapsedOffset()
                STATE_FULLY_EXPENDED -> getFullyExpendOffset()
                else -> when (stateHelper.lastState) {
                    STATE_FULLY_EXPENDED -> getCollapsedOffset()
                    STATE_NORMAL, STATE_EXPENDED -> getFullyExpendOffset()
                    else -> null
                }
            }?.let { animateOffsetTo(it) }
            return
        }

        when (stateHelper.nowState) {
            STATE_EXPENDED -> 0
            STATE_COLLAPSED -> getCollapsedOffset()
            STATE_FULLY_EXPENDED -> getFullyExpendOffset()
            STATE_NORMAL -> calculateSnapOffset(
                topAndBottomOffset, 0, getCollapsedOffset()
            )

            STATE_MIDDLE -> when (stateHelper.lastState) {
                STATE_FULLY_EXPENDED -> 0
                STATE_NORMAL,
                STATE_EXPENDED -> getFullyExpendOffset()

                else -> null
            }

            else -> null
        }?.let { animateOffsetTo(it) }
    }

    /**
     * 用于计算与当前位置最近的边
     * @param value 当前位置
     * @param snapTo 目标边
     * @return 返回最近的边的位置
     */
    private fun calculateSnapOffset(value: Int, vararg snapTo: Int): Int {
        var min = Int.MAX_VALUE
        var result = value
        snapTo.forEach { i ->
            val temp = abs(value - i)
            if (temp < min) {
                min = temp
                result = i
            }
        }
        return result
    }

    fun cancelAnimation() {
        mScrollAnimation?.cancel()
    }

    fun animateOffsetTo(
        offset: Int
    ) {
        mScrollAnimation = mScrollAnimation
            ?: SpringAnimation(
                this, HeaderOffsetFloatProperty(),
                topAndBottomOffset.toFloat()
            ).apply {
                spring.dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
                spring.stiffness = SpringForce.STIFFNESS_LOW
            }
        if (shouldSkipExpandedState(childWeakReference?.get())) {
            mScrollAnimation!!.spring.stiffness = 150f
        } else {
            mScrollAnimation!!.spring.stiffness = SpringForce.STIFFNESS_LOW
        }

        mScrollAnimation?.cancel()
        mScrollAnimation?.animateToFinalPosition(offset.toFloat())
    }

    /**
     * 用于计算阻尼衰减后的位置
     * @param oldOffset 前一个位置
     * @param newOffset 当前位置
     * @return 阻尼衰减后的目标位置
     */
    open fun checkDampOffset(oldOffset: Int, newOffset: Int): Int {
        var nextPosition = newOffset
        if (newOffset > oldOffset) {
            val percent = 1f - oldOffset.toFloat() / getMaxDragOffset()
            if (percent in 0F..1F) nextPosition =
                (oldOffset + (newOffset - oldOffset) * percent).toInt()
        }
        return nextPosition
    }

    /**
     * 滚动指定的距离
     * @param dy 滚动的距离
     */
    fun scroll(
        dy: Int,
        minOffset: Int = getCollapsedOffset(),
        maxOffset: Int = getFullyExpendOffset()
    ): Int {
        return setHeaderTopBottomOffset(
            checkDampOffset(topAndBottomOffset, topAndBottomOffset - dy),
            minOffset,
            maxOffset
        )
    }

    /**
     * 松手后根据速度进行滑动
     * @param velocityY
     */
    fun fling(
        velocityY: Float,
        minOffset: Int = getCollapsedOffset(),
        maxOffset: Int = getFullyExpendOffset(),
    ): Boolean {
        scroller = scroller ?: OverScroller(mContext)
        scroller!!.fling(
            0, topAndBottomOffset,            // startX / Y
            0, velocityY.roundToInt(),     // velocityX / Y
            0, 0,                       // minX / maxX
            minOffset, maxOffset                   // minY / maxY
        )

        if (shouldSkipExpandedState(childWeakReference?.get())) {
            when (stateHelper.nowState) {
                STATE_COLLAPSED, STATE_NORMAL -> animateOffsetTo(getCollapsedOffset())
                STATE_EXPENDED, STATE_MIDDLE, STATE_FULLY_EXPENDED -> snapToChildIfNeeded()
            }
            return true
        }
        when (stateHelper.nowState) {
            STATE_COLLAPSED,
            STATE_NORMAL,
            STATE_EXPENDED -> {
                animateOffsetTo(
                    calculateSnapOffset(scroller!!.finalY, 0, minOffset)
                )
            }

            STATE_MIDDLE,
            STATE_FULLY_EXPENDED -> {
                snapToChildIfNeeded()
            }
        }
        return true
    }

    inner class HeaderOffsetFloatProperty :
        FloatPropertyCompat<ExpendHeaderBehavior<V>>("header_offset") {
        override fun getValue(obj: ExpendHeaderBehavior<V>): Float {
            return obj.topAndBottomOffset.toFloat()
        }

        override fun setValue(obj: ExpendHeaderBehavior<V>, value: Float) {
            obj.setHeaderTopBottomOffset(value.roundToInt())
        }
    }
}