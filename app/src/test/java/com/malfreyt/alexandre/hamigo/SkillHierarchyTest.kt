package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test

class SkillHierarchyTest {
    @Test fun everyLeafHasOneCoherentPathAndFamiliesAreNotPersistentSkills() {
        val families=SkillHierarchy.families
        val leaves=families.flatMap {it.skillIds}
        assertEquals(leaves.size,leaves.distinct().size)
        assertTrue(leaves.none {it.startsWith("family:")})
        for(family in families) {
            assertTrue(family.id.startsWith("family:"))
            assertTrue(family.parentId==null||families.any {it.id==family.parentId})
        }
        assertEquals(listOf("family:electricity","family:dc"),SkillHierarchy.ancestry("ohm-law").map {it.id})
        assertTrue("energy-duration" in SkillHierarchy.descendants("family:electricity"))
        assertFalse("morse-listening" in SkillHierarchy.descendants("family:electricity"))
    }
    @Test fun emptyAndImportedEvidenceNeverBecomeParentMastery() {
        val family=SkillHierarchy.family("family:dc")!!
        assertEquals(0,SkillHierarchy.summary(family,emptyMap(),100).encountered)
        val seed=SkillLearning.seed(0,2)
        val reviews=mapOf("ohm-law" to seed,"hamigo-tools" to SkillReview(repetitions=3,interval=20.0))
        val summary=SkillHierarchy.summary(family,reviews,100)
        assertEquals(1,summary.encountered);assertEquals(1,summary.fragile);assertEquals(0,summary.consolidated)
        assertEquals(reviews,mapOf("ohm-law" to seed,"hamigo-tools" to SkillReview(repetitions=3,interval=20.0)))
        assertEquals(0,SkillHierarchy.summary(SkillHierarchy.family("family:learning")!!,reviews,100).encountered)
    }
    @Test fun familyPracticeKeepsUrgentQuestionsAndNeverMutatesEvidence() {
        fun q(id:String,skill:String="")=Question(id,id,listOf("Oui","Non"),0,"",skillIds=if(skill.isBlank())emptyList() else listOf(skill))
        val urgent=(0..5).map {q("due-$it")}
        val siblings=(0..15).map {q("sibling-$it","dc-current")}
        val other=(0..20).map {q("other-$it","morse-spacing")}
        val pool=urgent+siblings+other
        val uuid=urgent.associate {it.id to Review(due=1)}
        val weak=SkillLearning.record(null,"ohm-failed",false,1,0,"failure")
        val reviews=mapOf("ohm-law" to weak,"dc-current" to SkillReview(due=1_000_000,attempts=3,successes=3),"morse-spacing" to SkillReview(due=1_000_000))
        val before=reviews.toMap()
        var promotedSibling=false
        for(seed in 0..50) {
            val baseline=CourseRevisionBuilder.create(pool,uuid,10,100,seed)
            val selected=CourseRevisionBuilder.create(pool,uuid,reviews,{it.skillIds},10,100,seed)
            assertEquals(10,selected.size);assertEquals(10,selected.map {it.id}.distinct().size)
            assertEquals(urgent.map {it.id}.toSet(),selected.filter {it.id.startsWith("due-")}.map {it.id}.toSet())
            assertTrue(selected.count {it.id !in baseline.map {it.id}}<=2)
            if(selected.any {it.id.startsWith("sibling-")&&it.id !in baseline.map {it.id}})promotedSibling=true
            assertEquals(before,reviews)
        }
        assertTrue("A difficult family actually contributes related practice",promotedSibling)
    }
    @Test fun knownFamiliesNeverOpenUntouchedLessonsOrHamigoTools() {
        val a=Question("studied","",listOf("Oui","Non"),0,"",skillIds=listOf("ohm-law"))
        val b=a.copy(id="untouched-sibling",skillIds=listOf("dc-current"))
        val same=a.copy(id="untouched-same-concept")
        val tool=a.copy(id="tool",skillIds=listOf("hamigo-tools"))
        fun lesson(id:String,q:Question)=Lesson(id,id,"",emptyList(),"","",listOf(q),skillIds=q.skillIds)
        val reviews=mapOf("ohm-law" to SkillLearning.record(null,a.id,false,1,0,"a"),"dc-current" to SkillReview(due=1_000_000))
        val lessons=listOf(lesson("opened",a),lesson("untouched",b),lesson("untouched-same",same),lesson("welcome",tool))
        // Both fully completed and only partly attempted lessons retain the untouched-lesson gate,
        // even when the exact same concept already has imported evidence.
        for(completed in listOf(setOf("opened","welcome"),setOf("welcome"))) {
            val pool=CourseRevisionBuilder.eligible(lessons,completed,mapOf(a.id to Review()),reviews,{it.skillIds})
            assertEquals(listOf(a.id),pool.map {it.id})
            assertEquals(listOf(a.id),CourseRevisionBuilder.create(pool,emptyMap(),reviews,{it.skillIds},10,1_000_000,3).map {it.id})
        }
    }
    @Test fun familyPracticePrefersADifficultStudiedSiblingOverASolidSibling() {
        fun q(id:String,skill:String="")=Question(id,id,listOf("Oui","Non"),0,"",skillIds=if(skill.isBlank())emptyList() else listOf(skill))
        val weak=q("weak-sibling","ohm-law")
        val solid=q("solid-sibling","dc-current")
        val pool=(0..40).map {q("ordinary-$it")}+weak+solid
        val failed=SkillLearning.record(null,weak.id,false,1,0,"failure").copy(due=1_000_000)
        val reviews=mapOf("ohm-law" to failed,"dc-current" to SkillReview(due=1_000_000,repetitions=4,attempts=4,successes=4))
        var comparisons=0
        for(seed in 0..100) {
            val baseline=CourseRevisionBuilder.create(pool,emptyMap(),10,100,seed)
            if(baseline.none {it.id==weak.id||it.id==solid.id}) {
                val selected=CourseRevisionBuilder.create(pool,emptyMap(),reviews,{it.skillIds},10,100,seed)
                assertTrue(selected.any {it.id==weak.id})
                assertFalse(selected.any {it.id==solid.id})
                comparisons++
            }
        }
        assertTrue(comparisons>0)
    }
}
