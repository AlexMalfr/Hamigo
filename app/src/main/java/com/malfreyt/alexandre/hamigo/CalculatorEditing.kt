package com.malfreyt.alexandre.hamigo

/** Selection offsets are preserved across both keyboard and calculator-key edits. */
data class CalculatorEdit(val text: String, val start: Int = text.length, val end: Int = start)

object CalculatorEditing {
    private val tools=Regex("asin\\(|acos\\(|atan\\(|sqrt\\(|sin\\(|cos\\(|tan\\(|log\\(|ln\\(|exp\\(|10\\^\\(|1/\\(|Ans|\\^2")
    private fun tokenAt(text:String,offset:Int)=tools.findAll(text).firstOrNull {offset>it.range.first&&offset<=it.range.last}
    fun snap(text:String,offset:Int,forward:Boolean=false):Int {
        val point=offset.coerceIn(0,text.length)
        val token=tokenAt(text,point) ?: return point
        return if(forward)token.range.last+1 else token.range.first
    }
    fun move(input:CalculatorEdit,steps:Int):CalculatorEdit {
        var cursor=if(steps<0)minOf(input.start,input.end) else maxOf(input.start,input.end)
        repeat(kotlin.math.abs(steps).coerceAtMost(512)) {cursor=snap(input.text,cursor+if(steps<0)-1 else 1,steps>0)}
        return CalculatorEdit(input.text,cursor)
    }
    fun selectBackwards(input:CalculatorEdit,steps:Int):CalculatorEdit = input.copy(start=move(input, -steps.coerceAtLeast(0)).start,end=input.end)
    /** Removing an opener must not strand its matching closer, even after retained operands. */
    private fun erase(input:CalculatorEdit,from:Int,to:Int):CalculatorEdit {
        val stack=java.util.ArrayDeque<Int>()
        val closers=mutableSetOf<Int>()
        input.text.forEachIndexed {index,char->when(char) {
            '('->stack.addLast(index)
            ')'->if(stack.isNotEmpty()) {val opener=stack.removeLast();if(opener in from until to&&index>=to)closers+=index}
        }}
        return CalculatorEdit(input.text.filterIndexed {index,_->index !in from until to&&index !in closers},from)
    }
    fun press(input: CalculatorEdit, key: String): CalculatorEdit {
        val from = minOf(input.start, input.end).coerceIn(0, input.text.length)
        val to = maxOf(input.start, input.end).coerceIn(from, input.text.length)
        fun replace(value: String, cursor: Int = value.length, begin: Int = from, finish: Int = to): CalculatorEdit {
            val text = input.text.replaceRange(begin, finish, value)
            return if (text.length > 512) input else CalculatorEdit(text, begin + cursor)
        }
        fun wrap(prefix: String, suffix: String): CalculatorEdit {
            if (from != to) return replace(prefix + input.text.substring(from, to) + suffix)
            val begin = operandStart(input.text, from)
            if (begin != from) return replace(prefix + input.text.substring(begin, from) + suffix, begin = begin)
            return replace(prefix + suffix, prefix.length)
        }
        return when (key) {
            "C" -> CalculatorEdit("")
            "⌫" -> if (from != to) erase(input,from,to) else if (from > 0) {
                val begin=snap(input.text,from-1)
                erase(input,begin,to)
            } else input
            "(" -> if (from != to) replace("(" + input.text.substring(from, to) + ")") else replace("()", 1)
            ")" -> if (from == to && input.text.getOrNull(from) == ')') CalculatorEdit(input.text, from + 1) else replace(")")
            "x²" -> wrap("(", ")^2")
            "1/x" -> wrap("1/(", ")")
            "√", "10ˣ", "eˣ", "sin", "cos", "tan", "asin", "acos", "atan", "log", "ln" -> {
                val multiply=key=="10ˣ"&&from>0&&(input.text[from-1].isLetterOrDigit()||input.text[from-1] in ")π%")
                val prefix = (if(multiply)"×" else "")+when (key) { "√" -> "sqrt("; "10ˣ" -> "10^("; "eˣ" -> "exp("; else -> "$key(" }
                if (from != to) replace(prefix + input.text.substring(from, to) + ")") else replace(prefix + ")", prefix.length)
            }
            "EXP" -> replace("e")
            else -> replace(key)
        }
    }

    /** Find the last complete atom, including a function's name and nested parentheses. */
    private fun operandStart(text: String, end: Int): Int {
        if (end == 0) return end
        var start = end
        if (text[end - 1] == ')') {
            var depth = 0
            while (start > 0) {
                start--
                when (text[start]) { ')' -> depth++; '(' -> { depth--; if (depth == 0) break } }
            }
            if (depth != 0) return end
            val opener=tools.findAll(text).firstOrNull {it.value.endsWith("(")&&it.range.last==start}
            if(opener!=null)start=opener.range.first
            else while (start > 0 && text[start - 1].isLetter()) start--
        } else {
            while (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] in ".,π")) start--
            // Include a signed scientific exponent, e.g. 1e-6.
            if (start > 1 && text[start - 1] in "+−-" && text[start - 2] in "eE") {
                start -= 2
                while (start > 0 && (text[start - 1].isDigit() || text[start - 1] in ".,")) start--
            }
        }
        if(start>0&&text[start-1]=='^')start=operandStart(text,start-1)
        if(start>0&&text[start-1] in "+−-"&&(start==1||text[start-2] in "+−-×÷*/^("))start--
        return start
    }
}
