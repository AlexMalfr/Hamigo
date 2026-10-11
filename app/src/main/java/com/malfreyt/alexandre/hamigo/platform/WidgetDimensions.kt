package com.malfreyt.alexandre.hamigo.platform

import android.content.Context
import android.util.SizeF
import com.malfreyt.alexandre.hamigo.R

/** Shared by provider metadata, rendered previews and runtime fallbacks. */
internal object WidgetDimensions {
    fun defaultSize(context:Context,kind:HomeWidgetKind?):SizeF {
        val ids=when(kind) {
            HomeWidgetKind.STREAK->R.dimen.widget_streak_width to R.dimen.widget_streak_height
            HomeWidgetKind.GOAL->R.dimen.widget_goal_width to R.dimen.widget_goal_height
            HomeWidgetKind.WEEK->R.dimen.widget_week_width to R.dimen.widget_week_height
            null->R.dimen.widget_social_width to R.dimen.widget_social_height
        }
        val density=context.resources.displayMetrics.density
        return SizeF(context.resources.getDimension(ids.first)/density,context.resources.getDimension(ids.second)/density)
    }
}
