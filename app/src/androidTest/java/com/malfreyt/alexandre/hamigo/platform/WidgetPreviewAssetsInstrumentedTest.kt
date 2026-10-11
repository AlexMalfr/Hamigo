package com.malfreyt.alexandre.hamigo.platform

import android.content.res.Configuration
import android.graphics.*
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Reproducible picker assets, rendered by the exact native widget components. */
@RunWith(AndroidJUnit4::class)
class WidgetPreviewAssetsInstrumentedTest {
    @Test fun regeneratePickerAssetsFromTheRealWidgets() {
        check(Build.HARDWARE in listOf("ranchu","goldfish"))
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext.createConfigurationContext(Configuration(instrumentation.targetContext.resources.configuration).apply {densityDpi=320;fontScale=1f})
        val date=LocalDate.of(2026,10,9);val monday=date.minusDays(4)
        val daily=listOf(60,24,88,18,24,0,0)
        val snapshot=WidgetSnapshot(ReminderContent.build(ReminderState(24,60,(0L..4L).map {date.minusDays(it).toString()}.toSet()),date),daily.mapIndexed {i,x->WidgetDay(monday.plusDays(i.toLong()),x)})
        val own=ShareProgress("Alex",800,5,13,snapshot.weeklyXp)
        val team=SocialWidgetSnapshot(listOf(SocialWidgetMember(own,null,true),SocialWidgetMember(ShareProgress("Camille",980,7,16,270),null,false)))
        val directory=File(context.getExternalFilesDir(null),"widget-previews-0.49").apply {mkdirs()}
        listOf(HomeWidgetKind.STREAK,HomeWidgetKind.GOAL,HomeWidgetKind.WEEK,null).forEach {kind->
            val name=kind?.name?.lowercase() ?: "social";val size=WidgetDimensions.defaultSize(context,kind)
            val views=if(kind==null)SocialWidgetPresentation.views(context,team,size) else WidgetPresentation.views(context,kind,snapshot,size)
            instrumentation.runOnMainSync {
                val view=views.apply(context,FrameLayout(context));val w=(size.width*2).toInt();val h=(size.height*2).toInt()
                view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h)
                val bitmap=Bitmap.createBitmap(w,h+48,Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);view.draw(canvas)
                val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(232,245,239)}
                canvas.drawRoundRect(RectF(4f,h+6f,w-4f,h+46f),20f,20f,paint)
                paint.color=Color.rgb(8,127,130);paint.textSize=20f;paint.typeface=Typeface.DEFAULT_BOLD;paint.textAlign=Paint.Align.CENTER
                canvas.drawText("↔ Redimensionnable",w/2f,h+32f,paint)
                assertEquals(w,bitmap.width);assertEquals(h+48,bitmap.height)
                File(directory,"widget_preview_$name.webp").outputStream().use {bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS,100,it)}
                File(directory,"widget_preview_$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
                bitmap.recycle()
            }
        }
    }
}
