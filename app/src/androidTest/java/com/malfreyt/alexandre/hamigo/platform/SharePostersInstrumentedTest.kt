package com.malfreyt.alexandre.hamigo.platform

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class SharePostersInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun qrRemainsDecodableInsideTheSharedInvitationImage() {
        val link = FriendInvite.link("1234567890abcdef1234567890abcdef")
        val file = NativeShare.renderInviteImage(context, link)
        val image = BitmapFactory.decodeFile(file.absolutePath)
        try {
            val pixels = IntArray(image.width * image.height)
            image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
            val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, pixels))))
            assertEquals(link, decoded.text)
            assertEquals("1234567890abcdef1234567890abcdef", FriendInvite.parse(decoded.text))
        } finally { image.recycle(); file.delete() }
    }

    @Test
    fun sharedTeamImageIncludesDataBeyondTheSixVisibleRows() {
        val today = LocalDate.now()
        val own = ShareProgress("Éloïse 📻", 2048, 12, 30, 70, dailyXp = (6L downTo 0L).map {
            DailyPoint(today.minusDays(it).toString(), if (it % 2L == 0L) 20 else 0)
        })
        val friends = (1..12).map { index -> ShareProgress("Opérateur $index", 3000, index, 24, 100 + index) }
        val first = NativeShare.renderTeamImage(context, own, friends)
        val second = NativeShare.renderTeamImage(context, own, friends.mapIndexed { index, profile ->
            if (index == 0) profile.copy(weeklyXp = 0) else profile
        })
        val personal = NativeShare.renderProgressImage(context, own)
        val firstImage = BitmapFactory.decodeFile(first.absolutePath)
        val secondImage = BitmapFactory.decodeFile(second.absolutePath)
        val personalImage = BitmapFactory.decodeFile(personal.absolutePath)
        try {
            assertEquals(1080, firstImage.width)
            assertEquals(1350, firstImage.height)
            assertTrue(first.length() > 10_000)
            // This friend is outside the visible top five: their XP must still affect team totals.
            assertFalse(firstImage.sameAs(secondImage))
            assertFalse(firstImage.sameAs(personalImage))
        } finally {
            firstImage.recycle(); secondImage.recycle(); personalImage.recycle()
            first.delete(); second.delete(); personal.delete()
        }
    }
}
