package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ExamHistoryTest {
    private fun bank()=listOf("regulation","technique").flatMap {section->(0 until 60).map {i->
        Question("$section-$i","Question $i",listOf("A","B","C","D"),0,"Explication",topic="Sujet ${i%6}",section=section)
    }}
    private fun attempt(n:Int,score:Int=20):ExamAttempt {
        val questions=bank().filter {it.section=="regulation"}.take(20)+bank().filter {it.section=="technique"}.take(20)
        val responses=questions.mapIndexed {i,q->ExamResponse(i,q.id,q.section,q.topic,q.prompt,q.choices,0,if(i%20<score)0 else 1,false,q.explanation,"Exam1")}
        return ExamAttempt(UUID.nameUUIDFromBytes("attempt-$n".toByteArray()).toString(),1000L+n,1,1000,score,score,responses)
    }
    @Test fun aTrendNeedsThreeAttemptsAndDescribesTheNextScoreWithoutExceedingTheScale() {
        assertNull(ExamHistory.trend(listOf(attempt(0,8),attempt(1,10))){it.regulationScore/20f})
        assertEquals(.7f,ExamHistory.trend(listOf(attempt(2,12),attempt(0,8),attempt(1,10))){it.regulationScore/20f}!!,.0001f)
        assertEquals(1f,ExamHistory.trend(listOf(attempt(0,16),attempt(1,18),attempt(2,20))){it.totalScore/40f}!!,0f)
    }
    @Test fun omittedAnswersAreIncludedInTheTopicEvidenceWithoutBecomingSuccesses() {
        val base=attempt(0)
        val responses=base.responses.mapIndexed {i,r->if(i==0)r.copy(selectedAnswer=-1,omitted=true) else r}
        val summary=ExamHistory.topicMistakes(listOf(base.copy(regulationScore=19,responses=responses)))
        assertEquals(1,summary.single().errors)
        assertEquals("Sujet 0",summary.single().topic)
        assertTrue(summary.single().rate>0f)
    }
    @Test fun customizedExamsPreserveTheFormatAndEmphasizeRepeatedErrorsAcrossSeeds() {
        val history=(0..2).map {n->val a=attempt(n);val responses=a.responses.map {r->r.copy(selectedAnswer=if(r.topic=="Sujet 0")1 else 0)}
            a.copy(regulationScore=responses.take(20).count {it.correct},techniqueScore=responses.drop(20).count {it.correct},responses=responses)}
        var weak=0;var other=0
        repeat(100) {seed->
            val selected=ExamHistory.customizedQuestions(bank(),history,seed)
            assertEquals(40,selected.size);assertEquals(40,selected.map {it.id}.distinct().size)
            assertTrue(selected.take(20).all {it.section=="regulation"});assertTrue(selected.drop(20).all {it.section=="technique"})
            assertTrue(selected.take(20).map {it.topic}.distinct().size>=4);assertTrue(selected.drop(20).map {it.topic}.distinct().size>=4)
            weak+=selected.count {it.topic=="Sujet 0"};other+=selected.count {it.topic!="Sujet 0"}
        }
        assertTrue("weak=$weak, other=$other",weak>other/5*1.5)
        assertEquals(ExamHistory.customizedQuestions(bank(),history,17),ExamHistory.customizedQuestions(bank(),history,17))
        assertThrows(IllegalArgumentException::class.java){ExamHistory.customizedQuestions(bank(),history.take(2),1)}
    }
    @Test fun onlyBothFinalizedPartsProduceATranscriptAndItsIdentityStaysStable() {
        val questions=(bank().filter {it.section=="regulation"}.take(20)+bank().filter {it.section=="technique"}.take(20)).toMutableList()
        val session=Session("Examen blanc",questions,exam=true)
        assertThrows(IllegalArgumentException::class.java){ExamHistory.fromSession(session)}
        session.index=40;session.examIntroPending=false;session.finalizedExamParts+=listOf(0,1)
        session.regulationScore=20;session.techniqueScore=20
        session.questions.indices.forEach {session.responses[it]=SessionResponse(true,choiceIndex=0)}
        val first=ExamHistory.fromSession(session);val second=ExamHistory.fromSession(session,at=first.at+1)
        assertEquals(first.id,second.id);assertEquals(40,first.responses.size);assertTrue(first.passed)
        assertEquals(0,session.gain)
    }
}
