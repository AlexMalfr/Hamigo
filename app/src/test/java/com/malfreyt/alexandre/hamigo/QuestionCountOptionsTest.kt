package com.malfreyt.alexandre.hamigo

import org.junit.Test
import org.junit.Assert.*

class QuestionCountOptionsTest {
    @Test fun aReviewBankNeverOffersTooManyQuestionsAndAlwaysHasAnExactShortcut() {
        val usual=questionCountOptions()
        assertTrue(questionCountOptions(0).isEmpty())
        for(size in listOf(1,2,9,10,11,20,21,40,41,80,81,149,150,151,1000,1001,6000)) {
            val choices=questionCountOptions(size)
            assertTrue("No unavailable shortcut for a bank of $size",choices.all {it in 1..size})
            assertEquals("Exact-bank shortcut remains last",size,choices.last())
            assertEquals("No duplicate exact shortcut",choices.size,choices.distinct().size)
            assertTrue("Usual supported shortcuts remain",usual.filter {it<=size}.all {it in choices})
        }
        assertEquals(listOf(2),questionCountOptions(2))
    }
}
