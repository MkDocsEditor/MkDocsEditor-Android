package de.markusressel.mkdocseditor.view.fragment.base

import androidx.core.view.ViewCompat
import androidx.core.view.ViewPropertyAnimatorListener
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import android.view.View

class ScrollAwareFABBehavior : com.google.android.material.floatingactionbutton.FloatingActionButton.Behavior() {
    private var mIsAnimatingOut = false


    /**
     * Same animation that FloatingActionButton.Behavior
     * uses to show the FAB when the AppBarLayout enters
     *
     * @param floatingActionButton FAB
     */
    //
    private fun animateIn(floatingActionButton: com.google.android.material.floatingactionbutton.FloatingActionButton) {
        floatingActionButton
                .visibility = View
                .VISIBLE

        ViewCompat
                .animate(floatingActionButton)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1.0f)
                .setInterpolator(INTERPOLATOR)
                .setDuration(300)
                .withLayer()
                .setListener(object : ViewPropertyAnimatorListener {
                    override fun onAnimationStart(view: View) {
                        this@ScrollAwareFABBehavior
                                .mIsAnimatingOut = true
                        view
                                .visibility = View
                                .VISIBLE
                    }

                    override fun onAnimationCancel(view: View) {
                        this@ScrollAwareFABBehavior
                                .mIsAnimatingOut = false
                    }

                    override fun onAnimationEnd(view: View) {
                        this@ScrollAwareFABBehavior
                                .mIsAnimatingOut = false
                    }
                })
                .start()
    }

    /**
     * Same animation that FloatingActionButton.Behavior uses to
     * hide the FAB when the AppBarLayout exits
     *
     * @param floatingActionButton FAB
     */
    private fun animateOut(floatingActionButton: com.google.android.material.floatingactionbutton.FloatingActionButton) {
        ViewCompat
                .animate(floatingActionButton)
                .scaleX(0.0f)
                .scaleY(0.0f)
                .alpha(0.0f)
                .setInterpolator(INTERPOLATOR)
                .setDuration(150)
                .withLayer()
                .setListener(object : ViewPropertyAnimatorListener {
                    override fun onAnimationStart(view: View) {
                        this@ScrollAwareFABBehavior
                                .mIsAnimatingOut = true
                    }

                    override fun onAnimationCancel(view: View) {
                        this@ScrollAwareFABBehavior
                                .mIsAnimatingOut = false
                    }

                    override fun onAnimationEnd(view: View) {
                        this@ScrollAwareFABBehavior
                                .mIsAnimatingOut = false
                        view
                                .visibility = View
                                .INVISIBLE
                    }
                })
                .start()
    }

    override fun onStartNestedScroll(coordinatorLayout: androidx.coordinatorlayout.widget.CoordinatorLayout, child: com.google.android.material.floatingactionbutton.FloatingActionButton, directTargetChild: View, target: View, axes: Int, type: Int): Boolean {
        return axes == ViewCompat.SCROLL_AXIS_VERTICAL || super.onStartNestedScroll(coordinatorLayout, child, directTargetChild, target, axes, type)
    }

    override fun onNestedScroll(coordinatorLayout: androidx.coordinatorlayout.widget.CoordinatorLayout, child: com.google.android.material.floatingactionbutton.FloatingActionButton, target: View, dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int, type: Int) {
        super
                .onNestedScroll(coordinatorLayout, child, target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type)

        if (dyConsumed > 0 && !this.mIsAnimatingOut && child.visibility == View.VISIBLE) {
            animateOut(child)
        } else if (dyConsumed < 0 && child.visibility != View.VISIBLE) {
            animateIn(child)
        }
    }

    companion object {
        private val INTERPOLATOR = FastOutSlowInInterpolator()
    }

}