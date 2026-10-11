package com.malfreyt.alexandre.hamigo.platform

import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Daily totals only. Missing months inside the explicit coverage are known zeroes. */
data class PublicActivityHistory(val start:LocalDate,val end:LocalDate,val months:Map<YearMonth,List<Int>>) {
    init {
        require(!start.isAfter(end)&&ChronoUnit.MONTHS.between(YearMonth.from(start),YearMonth.from(end))<=1200)
        require(months.size<=1201)
        months.forEach {(month,values)->
            require(month>=YearMonth.from(start)&&month<=YearMonth.from(end)&&values.size==month.lengthOfMonth())
            values.forEachIndexed {i,x->require(x>=0);if(month.atDay(i+1)<start||month.atDay(i+1)>end)require(x==0)}
        }
    }
    fun xp(day:LocalDate):Int?=if(day<start||day>end)null else months[YearMonth.from(day)]?.get(day.dayOfMonth-1) ?: 0
    fun toJson()=JSONObject().put("start",start.toString()).put("end",end.toString()).put("months",JSONObject().also {json->
        months.toSortedMap().forEach {(month,values)->json.put(month.toString(),values.joinToString(","))}
    })
    fun forPublication():PublicActivityHistory {
        var size=100;val kept=linkedMapOf<YearMonth,List<Int>>()
        for((month,values) in months.toSortedMap(compareByDescending {it})) {
            val cost=values.joinToString(",").length+31
            if(size+cost>22_000)break
            size+=cost;kept[month]=values
        }
        return if(kept.size==months.size)this else copy(start=kept.keys.minOrNull()?.atDay(1) ?: end,months=kept)
    }
    /** Keep the social payload readable by older clients, including their 32 KiB limit. */
    fun trimOldestMonth():PublicActivityHistory {
        val kept=months.toSortedMap().entries.drop(1).associate {it.toPair()}
        return copy(start=kept.keys.minOrNull()?.atDay(1) ?: end,months=kept)
    }
    companion object {
        fun read(json:JSONObject):PublicActivityHistory {
            fun date(key:String)=json.getString(key).also {require(it.length==10)}.let(LocalDate::parse)
            val packed=json.getJSONObject("months");require(packed.length()<=1201)
            val months=packed.keys().asSequence().associate {key->
                require(key.length==7);val month=YearMonth.parse(key);val raw=packed.getString(key)
                require(raw.length<=341)
                val numbers=raw.split(',');require(numbers.size==month.lengthOfMonth())
                month to numbers.map {value->require(value.length in 1..10&&value.all {it in '0'..'9'});value.toInt().also {require(it>=0)}}
            }
            return PublicActivityHistory(date("start"),date("end"),months)
        }
        fun fromDailyXp(daily:JSONObject?,today:LocalDate):PublicActivityHistory {
            val earliest=today.minusMonths(1200).withDayOfMonth(1)
            val points=daily?.keys()?.asSequence()?.mapNotNull {key->
                val date=runCatching {LocalDate.parse(key)}.getOrNull() ?: return@mapNotNull null
                if(date<earliest||date>today)null else date to daily.optInt(key).coerceAtLeast(0)
            }?.toList().orEmpty()
            val start=minOf(points.minOfOrNull {it.first} ?: today,today.minusDays(13))
            val months=points.filter {it.second>0}.groupBy {YearMonth.from(it.first)}.mapValues {(month,days)->
                val totals=days.toMap();(1..month.lengthOfMonth()).map {totals[month.atDay(it)] ?: 0}
            }
            return PublicActivityHistory(start,today,months)
        }
    }
}
