package com.malfreyt.alexandre.hamigo.platform

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.util.SizeF
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import com.malfreyt.alexandre.hamigo.GitHubAvatarImages
import com.malfreyt.alexandre.hamigo.MainActivity
import com.malfreyt.alexandre.hamigo.PicoRenderer
import com.malfreyt.alexandre.hamigo.MascotMood
import com.malfreyt.alexandre.hamigo.MascotPose
import com.malfreyt.alexandre.hamigo.R
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal data class SocialWidgetSlot(val id:Int,val bounds:RectF,val text:String,val font:Float,val bold:Boolean=false,val centered:Boolean=false)
internal data class SocialWidgetRow(val member:SocialWidgetMember,val bounds:RectF,val avatar:RectF,val bar:RectF)
internal data class SocialWidgetComposition(val size:SizeF,val slots:List<SocialWidgetSlot>,val rows:List<SocialWidgetRow>,val pico:RectF)

/** The wide strip, compact rank and comparison rows each own their proportions. */
internal object SocialWidgetPresentation {
    private val names=listOf(R.id.social_name1,R.id.social_name2,R.id.social_name3,R.id.social_name4,R.id.social_name5)
    private val values=listOf(R.id.social_xp1,R.id.social_xp2,R.id.social_xp3,R.id.social_xp4,R.id.social_xp5)
    private val all=listOf(R.id.social_title,R.id.social_period,R.id.social_rank)+names+values+R.id.social_footer
    private val teal=Color.rgb(8,127,130);private val ink=Color.rgb(23,63,66);private val muted=Color.rgb(86,112,109)
    fun composition(snapshot:SocialWidgetSnapshot,size:SizeF):SocialWidgetComposition {
        val w=size.width.coerceAtLeast(40f);val h=size.height.coerceAtLeast(40f)
        val p=(minOf(w,h)*.09f).coerceIn(6f,16f)
        val slots=mutableListOf<SocialWidgetSlot>();val rows=mutableListOf<SocialWidgetRow>()
        val pico:RectF
        val members=snapshot.ranked;val own=members.firstOrNull {it.own};val rank=members.indexOf(own)+1
        fun slot(id:Int,l:Float,t:Float,r:Float,b:Float,text:String,font:Float,bold:Boolean=false,center:Boolean=false) {
            val bounds=RectF(maxOf(p,l),maxOf(p,t),minOf(w-p,r),minOf(h-p,b))
            if(bounds.width()>0&&bounds.height()>0)slots+=SocialWidgetSlot(id,bounds,text,font,bold,center)
        }
        val period=if(snapshot.today)"Aujourd’hui" else "7 derniers jours"
        fun selected(count:Int)=members.take(count).let {if(own!=null&&own !in it&&it.isNotEmpty())it.dropLast(1)+own else it}
        if(w<64f&&h<160f) {
            val titleH=if(h>=64f)12f else 0f
            val icon=minOf(w-2*p,h*.35f,32f)
            val top=p+titleH+if(titleH>0)3f else 0f
            pico=RectF((w-icon)/2,top,(w+icon)/2,top+icon)
            if(titleH>0)slot(R.id.social_title,p,p,w-p,p+titleH,"Équipe",9f,true,true)
            val number=if(members.size<2||members.any {snapshot.xp(it)==null})own?.let {snapshot.xp(it)?.let {x->"${compact(x)} XP"} ?: "—"} ?: "—" else "$rank/${members.size}"
            slot(R.id.social_rank,p,pico.bottom+2f,w-p,h-p-if(h>=100f)18f else 0f,number,26f,true,true)
            if(h>=100f)slot(R.id.social_period,p,h-p-15f,w-p,h-p,if(snapshot.today)"Ce jour" else "7 jours",9f,false,true)
        } else if(w<90f&&h>=160f) {
            slot(R.id.social_title,p,p,w-p,p+14f,"Équipe",10f,true,true)
            slot(R.id.social_period,p,p+16f,w-p,p+29f,if(snapshot.today)"Ce jour" else "7 jours",9f,false,true)
            val icon=minOf(w-2*p,h*.17f,58f)
            pico=RectF((w-icon)/2,p+33f,(w+icon)/2,p+33f+icon)
            val header=pico.bottom+8f
            val available=h-p-header;val count=(available/44f).toInt().coerceIn(1,3);val visible=selected(count)
            val step=(available/visible.size.coerceAtLeast(1)).coerceAtMost(69f)
            val start=header+maxOf(0f,(available-step*visible.size)/2f)
            visible.forEachIndexed {i,m->
                val y=start+i*step;val d=minOf(24f,w-2*p)
                slot(values[i],p,y+27f,w-p,y+step-7f,snapshot.xp(m)?.let {"${compact(it)} XP"} ?: "—",13f,true,true)
                rows+=SocialWidgetRow(m,RectF(p-2,y-2,w-p+2,y+step-2),RectF(w/2-d/2,y,w/2+d/2,y+d),RectF(p,y+step-2,w-p,y+step))
            }
        } else if(w<90f||h<125f&&w<140f||h<64f&&w<220f) {
            val icon=minOf(w*.30f,h*.30f,37f)
            pico=RectF(w-p-icon,p,w-p,p+icon)
            slot(R.id.social_title,p,p,pico.left-3f,p+minOf(14f,icon),"Équipe",10f,true)
            val number=if(members.size<2||members.any {snapshot.xp(it)==null})own?.let {"${compact(snapshot.xp(it) ?: 0)} XP"} ?: "—" else "$rank/${members.size}"
            slot(R.id.social_rank,p,pico.bottom+2f,w-p,h-p-if(h>=76)18f else 0f,number,26f,true,true)
            if(h>=76)slot(R.id.social_period,p,h-p-15,w-p,h-p,if(snapshot.today)"Ce jour" else "7 jours",10f,false,true)
        } else if(h<110f&&w>=140f||w/h>=2.3f&&h<=116f) {
            val icon=minOf(h-2*p,w*.14f,68f)
            pico=RectF(p,(h-icon)/2,p+icon,(h+icon)/2)
            val infoLeft=pico.right+6f
            val headerW=infoLeft+(w*.19f).coerceIn(42f,76f)
            val flat=h<64f
            slot(R.id.social_title,infoLeft,p,headerW-6,p+if(flat)14f else 20f,"Équipe",if(flat)11f else 14f,true)
            slot(R.id.social_period,infoLeft,p+if(flat)16f else 22f,headerW-6,p+if(flat)28f else 37f,if(snapshot.today)"Ce jour" else "7 jours",if(flat)9f else 10f)
            if(h>=85f&&members.size>1)slot(R.id.social_rank,infoLeft,p+41f,headerW-6,h-p,"$rank/${members.size}",16f,true)
            val count=((w-headerW-p)/(if(h<85f)62f else 80f)).toInt().coerceIn(1,3)
            val visible=selected(count);val step=(w-headerW-p)/visible.size.coerceAtLeast(1)
            visible.forEachIndexed {i,m->
                val l=headerW+i*step;val right=l+step-7f;val avatar=if(flat)RectF(l,h/2-10,l+20,h/2+10) else RectF(l,p,l+25f,p+25f)
                if(flat)slot(values[i],l+26f,p,right,h-p-5f,snapshot.xp(m)?.let {"${compact(it)} XP"} ?: "—",14f,true)
                else {
                    slot(names[i],l+31f,p,right,p+17f,if(m.own)"Toi" else m.profile.name,11f,true)
                    slot(values[i],l+31f,p+18f,right,h-p-7f,snapshot.xp(m)?.let {"${compact(it)} XP"} ?: "—",20f,true)
                }
                rows+=SocialWidgetRow(m,RectF(l-4f,p-3f,right+3f,h-p),avatar,RectF(l,h-p-3f,right,h-p))
            }
        } else {
            val narrow=w<155f
            val stacked=narrow&&h>=230f
            val icon=minOf(if(stacked)w-2*p else w*.29f,h*.22f,88f)
            pico=if(stacked)RectF((w-icon)/2,p+39f,(w+icon)/2,p+39f+icon) else RectF(w-p-icon,p,w-p,p+icon)
            val titleRight=if(stacked)w-p else pico.left-8f
            slot(R.id.social_title,p,p,titleRight,p+21,"Équipe",if(w>=240f&&h>=240f)20f else if(narrow)14f else 16f,true)
            slot(R.id.social_period,p,p+23,titleRight,p+38,if(narrow)if(snapshot.today)"Ce jour" else "7 jours" else period,11f)
            val header=maxOf(p+45f,pico.bottom+8f);val footer=if(members.size==1)23f else 0f
            val available=(h-p-footer-header).coerceAtLeast(26f)
            val rowMinimum=if(narrow)44f else 32f
            val count=(available/rowMinimum).toInt().coerceIn(1,5)
            val visible=selected(count)
            if(members.size==1) {
                slot(R.id.social_footer,p,h-p-20f,w-p,h-p,"Invite tes amis",11f,false,true)
            }
            val rowH=(available/visible.size.coerceAtLeast(1)).coerceAtMost(if(h>=280f)64f else 50f)
            val start=header+maxOf(0f,(available-rowH*visible.size)/2f)
            visible.forEachIndexed {i,m->
                val y=start+i*rowH;val avatarSize=minOf(29f,rowH-10f);val avatar=RectF(p+3f,y+4f,p+3f+avatarSize,y+4f+avatarSize)
                val left=avatar.right+9f;val right=w-p-4f
                if(w<155f) {
                    slot(names[i],left,y+1f,right,y+18f,if(m.own)"Toi" else m.profile.name,11f,true)
                    slot(values[i],left,y+19f,right,y+33f,snapshot.xp(m)?.let {"${compact(it)} XP"} ?: "—",16f,true)
                } else {
                    val valueW=(w*.29f).coerceIn(55f,90f)
                    slot(names[i],left,y+2f,right-valueW-5f,y+22f,if(m.own)"Toi" else m.profile.name,13f,true)
                    slot(values[i],right-valueW,y+2f,right,y+22f,snapshot.xp(m)?.let {"${compact(it)} XP"} ?: "—",15f,true)
                }
                rows+=SocialWidgetRow(m,RectF(p-3f,y-2f,w-p+3f,y+rowH-4f),avatar,RectF(left,y+rowH-7f,right,y+rowH-4f))
            }
        }
        return SocialWidgetComposition(SizeF(w,h),slots,rows,pico)
    }
    fun views(context:Context,snapshot:SocialWidgetSnapshot,size:SizeF,pixelBudget:Float=520_000f,transparent:Boolean=false,lightText:Boolean=true):RemoteViews {
        val layout=composition(snapshot,size);val w=layout.size.width;val h=layout.size.height
        val density=context.resources.displayMetrics.density
        fun px(x:Float)=(x*density).roundToInt().coerceAtLeast(0)
        val textColor=if(transparent&&lightText)Color.WHITE else ink
        val detailColor=if(transparent&&lightText)Color.rgb(217,239,234) else muted
        return RemoteViews(context.packageName,R.layout.widget_social).apply {
            val corner=(minOf(w,h)*.21f).coerceAtMost(28f)
            setInt(android.R.id.background,"setBackgroundResource",when {corner>=28f->R.drawable.widget_outline_28;corner>=24f->R.drawable.widget_outline_24;corner>=20f->R.drawable.widget_outline_20;corner>=16f->R.drawable.widget_outline_16;corner>=12f->R.drawable.widget_outline_12;else->R.drawable.widget_outline_8})
            setImageViewBitmap(R.id.widget_art,artwork(context,snapshot,layout,pixelBudget,transparent,lightText))
            all.forEach {id->
                val slot=layout.slots.firstOrNull {it.id==id};setViewVisibility(id,if(slot==null)View.GONE else View.VISIBLE)
                if(slot!=null) {
                    setTextViewText(id,slot.text);setTextColor(id,if(id==R.id.social_title)if(transparent&&lightText)Color.rgb(196,239,227) else teal else if(id in listOf(R.id.social_footer,R.id.social_period))detailColor else textColor)
                    setViewPadding(id,px(slot.bounds.left),px(slot.bounds.top),px(w-slot.bounds.right),px(h-slot.bounds.bottom))
                    setInt(id,"setGravity",Gravity.CENTER_VERTICAL or if(slot.centered)Gravity.CENTER_HORIZONTAL else Gravity.START)
                    val font=Paint(Paint.ANTI_ALIAS_FLAG).apply {textSize=slot.font*context.resources.displayMetrics.scaledDensity;typeface=if(slot.bold)Typeface.DEFAULT_BOLD else Typeface.DEFAULT}
                    val metrics=font.fontMetrics
                    // Names ellipsize rather than becoming illegibly tiny; numbers fit completely.
                    val widthScale=if(id in names)1f else minOf(1f,(px(slot.bounds.width())-2f)/font.measureText(slot.text).coerceAtLeast(1f))
                    val heightScale=minOf(1f,(px(slot.bounds.height())-2f)/(metrics.descent-metrics.ascent).coerceAtLeast(1f))
                    setTextViewTextSize(id,TypedValue.COMPLEX_UNIT_PX,(font.textSize*minOf(widthScale,heightScale)).coerceAtLeast(1f))
                }
            }
            val intent=Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("hamigo_route","friends")
            setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(context,2803,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            setContentDescription(R.id.widget_root,"Hamigo, équipe, ${if(snapshot.today)"aujourd’hui" else "7 derniers jours"}. "+snapshot.ranked.joinToString(". "){"${if(it.own)"Toi" else it.profile.name} : ${snapshot.xp(it)?.let {x->"$x XP"} ?: "données manquantes"}"}+". Ouvrir l’équipe.")
        }
    }
    private fun artwork(context:Context,snapshot:SocialWidgetSnapshot,layout:SocialWidgetComposition,budget:Float,transparent:Boolean,light:Boolean):Bitmap {
        val w=layout.size.width;val h=layout.size.height;val scale=minOf(context.resources.displayMetrics.density,2.5f,1400f/maxOf(w,h),sqrt(budget/(w*h)))
        val bitmap=Bitmap.createBitmap((w*scale).roundToInt().coerceAtLeast(1),(h*scale).roundToInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);canvas.scale(scale,scale);val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        if(!transparent) {
            paint.shader=LinearGradient(0f,0f,w,h,Color.rgb(225,245,241),Color.rgb(207,234,227),Shader.TileMode.CLAMP)
            val radius=(minOf(w,h)*.21f).coerceAtMost(28f);canvas.drawRoundRect(RectF(0f,0f,w,h),radius,radius,paint);paint.shader=null
        }
        val own=snapshot.members.firstOrNull {it.own}
        val reached=own?.let {member->member.profile.dailyGoal?.let {goal->SocialActivity.todayXp(member.profile)?.let {it>=goal}}}==true
        PicoRenderer.draw(canvas,layout.pico,if(reached)MascotMood.CELEBRATE else MascotMood.HAPPY,if(reached)MascotPose.DANCE else MascotPose.WAVE,.3f)
        val maximum=snapshot.members.maxOfOrNull {snapshot.xp(it) ?: 0}?.coerceAtLeast(1) ?: 1
        val cache=GitHubAvatarImages.cache(context)
        layout.rows.forEach {row->
            val own=row.member.own
            if(own) {paint.color=if(transparent)if(light)Color.argb(20,255,255,255) else Color.argb(20,8,127,130) else Color.argb(180,255,255,255);canvas.drawRoundRect(row.bounds,10f,10f,paint)}
            val save=canvas.save();canvas.clipPath(Path().apply {addOval(row.avatar,Path.Direction.CW)})
            val avatar=cache.cached(row.member.identity?.avatarUrl)
            if(avatar!=null) {val side=minOf(avatar.width,avatar.height);val x=(avatar.width-side)/2;val y=(avatar.height-side)/2;paint.color=Color.WHITE;paint.isFilterBitmap=true;canvas.drawBitmap(avatar,Rect(x,y,x+side,y+side),row.avatar,paint)}
            else {paint.color=if(own)teal else Color.rgb(116,162,157);canvas.drawOval(row.avatar,paint);paint.color=Color.WHITE;paint.textSize=row.avatar.height()*.43f;paint.typeface=Typeface.DEFAULT_BOLD;paint.textAlign=Paint.Align.CENTER;canvas.drawText(row.member.profile.name.trim().take(1).uppercase(Locale.FRENCH).ifBlank {"?"},row.avatar.centerX(),row.avatar.centerY()-(paint.fontMetrics.ascent+paint.fontMetrics.descent)/2,paint)}
            canvas.restoreToCount(save)
            paint.color=if(transparent&&light)Color.argb(45,255,255,255) else Color.argb(28,8,127,130);canvas.drawRoundRect(row.bar,2f,2f,paint)
            val xp=snapshot.xp(row.member)
            if(xp!=null&&xp>0) {
                paint.color=if(snapshot.today&&row.member.profile.dailyGoal?.let {xp>=it}==true)Color.rgb(232,190,75) else if(own)if(transparent&&light)Color.rgb(153,231,210) else teal else Color.rgb(220,129,108)
                canvas.drawRoundRect(RectF(row.bar.left,row.bar.top,row.bar.left+row.bar.width()*xp/maximum,row.bar.bottom),2f,2f,paint)
            }
        }
        return bitmap
    }
    private fun compact(x:Int)=when {x>=1_000_000->String.format(Locale.FRENCH,"%.1f M",x/1_000_000f);x>=10_000->"${x/1000} k";else->x.toString()}
}
