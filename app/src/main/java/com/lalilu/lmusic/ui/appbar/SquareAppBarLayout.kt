package com.lalilu.lmusic.ui.appbar

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout
import com.lalilu.lmusic.utils.AntiErrorTouchEvent
import com.lalilu.lmusic.utils.DeviceUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

@AndroidEntryPoint
class SquareAppBarLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppBarLayout(context, attrs, defStyleAttr), CoroutineScope, AntiErrorTouchEvent {
    @Inject
    lateinit var helper: AppBarStatusHelper
    private val zoomBehavior = AppBarZoomBehavior(helper, context, null)

    override val coroutineContext: CoroutineContext = Dispatchers.IO
    override val rect = Rect(0, 0, 0, 0)
    override val interceptSize = 100

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (changed && helper.currentState == STATE_FULLY_EXPENDED) {
            this.layout(l, t, r, DeviceUtil.getHeight(context))
        }
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        updateInterceptRect(height, height - interceptSize)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean =
        if (checkTouchEvent(event)) true
        else super.onTouchEvent(event)

    override fun whenToIntercept(): Boolean = helper.currentState == STATE_FULLY_EXPENDED

    override fun getBehavior(): CoordinatorLayout.Behavior<AppBarLayout> = zoomBehavior
}