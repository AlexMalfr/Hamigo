package com.malfreyt.alexandre.hamigo.platform

import android.Manifest
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SocialWidgetInstrumentedTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val directory get()=File(context.getExternalFilesDir(null),"social-0.49/widgets-font${context.resources.configuration.fontScale}").apply {mkdirs()}
    private fun snapshot():SocialWidgetSnapshot {
        val date=LocalDate.now()
        fun profile(name:String,x:Int)=ShareProgress(name,x*7,3,8,x*7,dailyXp=(6L downTo 0L).map {DailyPoint(date.minusDays(it).toString(),x)},dailyGoal=60)
        val own=profile("Alex Démo",24).copy(weeklyXp=96,dailyXp=listOf(12,0,30,12,18,0,24).mapIndexed {i,x->DailyPoint(date.minusDays((6-i).toLong()).toString(),x)})
        val cam=profile("Camille Démo",60).copy(weeklyXp=210,dailyXp=listOf(12,24,0,30,24,60,60).mapIndexed {i,x->DailyPoint(date.minusDays((6-i).toLong()).toString(),x)})
        return SocialWidgetSnapshot(listOf(SocialWidgetMember(own,null,true),SocialWidgetMember(cam,null,false),SocialWidgetMember(profile("Noa Démo",15),null,false)))
    }
    private fun options(size:SizeF)=Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,size.width.toInt());putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,size.width.toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,size.height.toInt());putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,size.height.toInt())
        putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,arrayListOf(size))
    }
    private fun measure(view:View,size:SizeF) {
        val d=context.resources.displayMetrics.density
        view.measure(View.MeasureSpec.makeMeasureSpec((size.width*d).toInt(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec((size.height*d).toInt(),View.MeasureSpec.EXACTLY));view.layout(0,0,view.measuredWidth,view.measuredHeight)
    }
    private fun save(bitmap:Bitmap,name:String) {File(directory,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
    private fun checkNative(view:View):Unit {
        fun descend(v:View) {
            if(v is TextView&&v.visibility==View.VISIBLE) {
                val width=v.width-v.compoundPaddingLeft-v.compoundPaddingRight;val height=v.height-v.compoundPaddingTop-v.compoundPaddingBottom
                assertTrue("No text viewport: ${v.text}",width>0&&height>0)
                assertNotNull(v.layout);assertTrue("Text clipped vertically: ${v.text}",v.layout.height<=height)
                if(v.id !in setOf(R.id.social_name1,R.id.social_name2,R.id.social_name3,R.id.social_name4,R.id.social_name5)) {
                    assertEquals("Text truncated: ${v.text}",0,v.layout.getEllipsisCount(0))
                }
            }
            if(v is ViewGroup)(0 until v.childCount).forEach {descend(v.getChildAt(it))}
        }
        descend(view);assertTrue(view.findViewById<View>(R.id.widget_root).hasOnClickListeners())
    }
    @Test fun atlasCoversNarrowShortTallAndWideCompositionsWithSafeNativeText() {
        check(Build.HARDWARE in listOf("ranchu","goldfish"))
        val sides=listOf(40,72,96,120,180,240,360)
        val width=48+sides.sum()+sides.size*16;val height=48+sides.sum()+sides.size*34
        val board=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);val canvas=Canvas(board);canvas.drawColor(Color.rgb(53,69,78))
        val label=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.WHITE;textSize=12f}
        val density=context.resources.displayMetrics.density
        var y=40f
        sides.forEach {h->var x=32f;sides.forEach {w->
            val size=SizeF(w.toFloat(),h.toFloat());val response=SocialWidgetPresentation.views(context,snapshot(),size)
            val layout=SocialWidgetPresentation.composition(snapshot(),size)
            assertTrue(layout.pico.width()>0&&layout.pico.left>=6f&&layout.pico.right<=w-6f)
            assertTrue(layout.pico.top>=6f&&layout.pico.bottom<=h-6f)
            layout.slots.forEach {assertFalse("Pico covers ${it.text} at $size",RectF.intersects(layout.pico,it.bounds))}
            layout.rows.forEach {assertFalse("Pico covers an avatar at $size",RectF.intersects(layout.pico,it.avatar))}
            instrumentation.runOnMainSync {
                val view=response.apply(context,FrameLayout(context));measure(view,size);checkNative(view)
                canvas.drawText("$w × $h",x,y+13,label);val checkpoint=canvas.save();canvas.translate(x,y+19);canvas.scale(1/density,1/density);view.draw(canvas);canvas.restoreToCount(checkpoint)
                if("${w}x$h" in setOf("40x40","72x180","96x96","120x180","240x180","360x72","360x240")) {
                    val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(image));save(image,"native-${w}x$h")
                }
            };x+=w+16
        };y+=h+34}
        save(board,"atlas-social")
    }
    @Test fun aRealHostAcceptsResponsiveSizesTransparencyAndDailyComparison() {
        check(Build.HARDWARE in listOf("ranchu","goldfish"));val manager=AppWidgetManager.getInstance(context);val host=AppWidgetHost(context,2849)
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.BIND_APPWIDGET)
        var id=-1
        try {
            id=host.allocateAppWidgetId();assertTrue(manager.bindAppWidgetIdIfAllowed(id,ComponentName(context,SocialWidgetProvider::class.java),options(SizeF(240f,180f))))
            val info=manager.getAppWidgetInfo(id);assertEquals(ComponentName(context,WidgetConfigurationActivity::class.java),info.configure)
            val sizes=listOf(SizeF(40f,40f),SizeF(72f,220f),SizeF(120f,180f),SizeF(240f,180f),SizeF(360f,80f),SizeF(360f,240f))
            val all=options(sizes.first()).apply {putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,ArrayList(sizes))}
            listOf(false,true).forEach {daily->
                assertTrue(WidgetSettings.save(context,id,transparent=daily,lightText=true,socialToday=daily))
                assertEquals(daily,WidgetSettings.socialToday(context,id))
                val response=SocialWidgets.responsiveViews(context,snapshot().copy(today=daily),all,id)
                manager.updateAppWidget(id,response)
                instrumentation.runOnMainSync {
                    val view=host.createView(context,id,info);view.setPadding(0,0,0,0)
                    sizes.forEach {size->
                        view.updateAppWidgetSize(Bundle(),listOf(size));view.updateAppWidget(response);measure(view,size);checkNative(view)
                        if(size.width>=240)assertTrue(view.findViewById<TextView>(R.id.social_xp1).text.contains(if(daily)"60 XP" else "210 XP"))
                        if(daily) {
                            val bitmap=(view.findViewById<ImageView>(R.id.widget_art).drawable as BitmapDrawable).bitmap
                            assertEquals("Transparent tile must preserve alpha at the corner",0,Color.alpha(bitmap.getPixel(0,0)))
                        }
                        val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);val canvas=Canvas(image);canvas.drawColor(Color.rgb(48,62,74));view.draw(canvas)
                        save(image,"host-${if(daily)"daily-transparent" else "week"}-${size.width.toInt()}x${size.height.toInt()}")
                    }
                }
            }
        } finally {if(id>=0){host.deleteAppWidgetId(id);WidgetSettings.delete(context,intArrayOf(id))};host.deleteHost();instrumentation.uiAutomation.dropShellPermissionIdentity()}
    }
}
