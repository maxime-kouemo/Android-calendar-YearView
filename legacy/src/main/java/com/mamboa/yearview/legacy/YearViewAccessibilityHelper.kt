package com.mamboa.yearview.legacy

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper

/**
 * Exposes every day cell of a [YearView] as an accessibility **virtual view**.
 *
 * Before this existed the entire calendar was a single accessibility node with one
 * long content description: a screen-reader user could hear a summary of the current
 * selection but could not move between days, and could not select one at all. With an
 * [ExploreByTouchHelper] installed, TalkBack treats each day as an individually
 * focusable, clickable element, so explore-by-touch and swipe navigation both work.
 *
 * ## Virtual view IDs
 *
 * A day is identified by `monthIndex * `[ID_MONTH_STRIDE]` + dayOfMonth`. The stride is
 * larger than the longest month, so the encoding is unambiguous and — because
 * `dayOfMonth` is never `0` — no valid day ever collides with
 * [ExploreByTouchHelper.HOST_ID].
 *
 * IDs must stay **stable across layout passes**, otherwise accessibility focus jumps
 * whenever the view is re-measured. Deriving them from the date rather than from an
 * iteration counter guarantees that.
 */
internal class YearViewAccessibilityHelper(
    private val host: YearView
) : ExploreByTouchHelper(host) {

    /** Scratch rectangle reused by [onPopulateNodeForVirtualView] to avoid allocation. */
    private val tempBounds = Rect()

    override fun getVirtualViewAt(x: Float, y: Float): Int {
        val date = host.dayAt(x.toInt(), y.toInt()) ?: return HOST_ID
        return virtualIdOf(date.month - 1, date.day)
    }

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        host.forEachVisibleDay { monthIndex, dayOfMonth ->
            virtualViewIds.add(virtualIdOf(monthIndex, dayOfMonth))
        }
    }

    override fun onPopulateNodeForVirtualView(
        virtualViewId: Int,
        node: AccessibilityNodeInfoCompat
    ) {
        val monthIndex = monthIndexOf(virtualViewId)
        val dayOfMonth = dayOfMonthOf(virtualViewId)

        node.className = Button::class.java.name
        node.contentDescription = host.describeDay(monthIndex, dayOfMonth)
        node.isFocusable = true
        node.isClickable = true
        node.isSelected = host.isDaySelected(monthIndex, dayOfMonth)
        node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)

        // ExploreByTouchHelper throws IllegalStateException if a node is returned with
        // empty bounds, so fall back to a 1px rect for a day that is momentarily
        // unpositioned (for example a query arriving before the first layout pass).
        if (!host.dayCellBoundsInto(tempBounds, monthIndex, dayOfMonth)) {
            tempBounds.set(0, 0, 1, 1)
        }
        @Suppress("DEPRECATION")
        node.setBoundsInParent(tempBounds)
    }

    override fun onPerformActionForVirtualView(
        virtualViewId: Int,
        action: Int,
        arguments: Bundle?
    ): Boolean {
        if (action != AccessibilityNodeInfo.ACTION_CLICK) return false
        val handled = host.activateDay(monthIndexOf(virtualViewId), dayOfMonthOf(virtualViewId))
        if (handled) {
            // Selection changed the node's `isSelected` state and possibly its
            // description, so the cached node must be rebuilt.
            invalidateVirtualView(virtualViewId)
        }
        return handled
    }

    private companion object {
        /**
         * Multiplier separating the month component of a virtual ID from the day
         * component. Any value above 31 works; 100 keeps IDs readable while debugging.
         */
        const val ID_MONTH_STRIDE = 100

        fun virtualIdOf(monthIndex: Int, dayOfMonth: Int): Int =
            monthIndex * ID_MONTH_STRIDE + dayOfMonth

        fun monthIndexOf(virtualViewId: Int): Int = virtualViewId / ID_MONTH_STRIDE

        fun dayOfMonthOf(virtualViewId: Int): Int = virtualViewId % ID_MONTH_STRIDE
    }
}
