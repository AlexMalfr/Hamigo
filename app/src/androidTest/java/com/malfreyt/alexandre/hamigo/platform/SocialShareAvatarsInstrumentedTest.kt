package com.malfreyt.alexandre.hamigo.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SocialShareAvatarsInstrumentedTest {
    @Test fun roundAvatarsStayWithTheirOwnerAfterRankingAndAppearInEveryPersonalTemplate() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val day=LocalDate.now()
        fun profile(name:String,xp:Int)=ShareProgress(name,xp*7,3,8,xp*7,dailyXp=(6L downTo 0L).map {DailyPoint(day.minusDays(it).toString(),xp)})
        fun avatar(color:Int)=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888).apply {
            eraseColor(color);val canvas=Canvas(this);val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {this.color=Color.WHITE}
            canvas.drawCircle(64f,45f,17f,paint);canvas.drawOval(32f,77f,96f,138f,paint)
        }
        val own=avatar(Color.rgb(83,146,173));val cam=avatar(Color.rgb(193,120,86));val noa=avatar(Color.rgb(139,121,177))
        val mine=profile("Alex Démo",24);val others=listOf(profile("Noa Démo",30),profile("Camille Démo",60))
        val directory=File(context.getExternalFilesDir(null),"social-0.49").apply {mkdirs()}
        fun inspect(file:File,name:String,x:Int,y:Int,expected:Int) {
            val bitmap=BitmapFactory.decodeFile(file.absolutePath)
            try {assertEquals(expected,bitmap.getPixel(x,y));assertEquals(1080,bitmap.width);file.copyTo(File(directory,"$name.png"),overwrite=true)}finally{bitmap.recycle();file.delete()}
        }
        try {
            inspect(NativeShare.renderProgressImage(context,mine,own),"share-personal-poster",98,248,own.getPixel(64,20))
            inspect(NativeShare.renderResultsImage(context,ShareResults("Alex Démo","Cours · premiers contacts",4,5,180_000,0,24),own),"share-results-poster",98,252,own.getPixel(64,20))
            val team=NativeShare.renderTeamImage(context,mine,others,mapOf(0 to own,1 to noa,2 to cam))
            val bitmap=BitmapFactory.decodeFile(team.absolutePath)
            try {
                assertEquals(cam.getPixel(64,20),bitmap.getPixel(177,615))
                assertEquals(noa.getPixel(64,20),bitmap.getPixel(177,680))
                assertEquals(own.getPixel(64,20),bitmap.getPixel(177,745))
                // Square bitmap corners are clipped away, leaving the card colour.
                assertEquals(Color.WHITE,bitmap.getPixel(153,611))
                team.copyTo(File(directory,"share-team-poster.png"),overwrite=true)
            } finally {bitmap.recycle();team.delete()}
            inspect(NativeShare.renderInviteImage(context,FriendInvite.link("1".repeat(32)),"Alex Démo",own),"share-invite-poster",95,279,own.getPixel(64,20))
        } finally {own.recycle();cam.recycle();noa.recycle()}
    }
}
