package com.malfreyt.alexandre.hamigo

/** A conservative, non-evaluating math tree. Unknown notation stays verbatim in the UI. */
internal sealed interface Formula {
    data class Atom(val value:String,val italic:Boolean=true):Formula
    data class Join(val left:Formula,val symbol:String,val right:Formula):Formula
    data class Fraction(val numerator:Formula,val denominator:Formula):Formula
    data class Root(val value:Formula):Formula
    data class Power(val value:Formula,val exponent:Formula):Formula
    data class Index(val value:Formula,val index:Formula):Formula
    data class Group(val value:Formula):Formula
}

internal object MathFormula {
    data class Fragment(val source:String,val formula:Formula?=null)
    private val equality=Regex("(?<![\\p{L}\\d])[UIRPWELSCZVAFXYTQHufrtlsωΩπλρτ](?:eff|max|min|moy)?\\d*\\s*=")
    private val proseBoundary=Regex("\\n|[?!;:]|(?<!\\d)[.,]|[.,](?!\\d)|\\s+(?:et|puis|pour|soit|avec|donne|alors|quelle|quel|calculer|calcule|en|est|vaut|à)\\b",RegexOption.IGNORE_CASE)
    fun hasPossibleEquality(text:String)=equality.containsMatchIn(text)
    fun fragments(text:String):List<Fragment> {
        val result=mutableListOf<Fragment>();var end=0
        for(match in equality.findAll(text)) {
            if(match.range.first<end)continue
            val afterEquals=match.range.last+1
            val stop=proseBoundary.find(text,afterEquals)?.range?.first ?: text.length
            val candidate=text.substring(match.range.first,stop).trimEnd()
            val formula=parse(candidate) ?: continue
            if(match.range.first>end)result+=Fragment(text.substring(end,match.range.first))
            result+=Fragment(candidate,formula);end=match.range.first+candidate.length
        }
        if(end<text.length)result+=Fragment(text.substring(end))
        return result.ifEmpty {listOf(Fragment(text))}
    }
    fun parse(text:String):Formula? {
        if(text.length>240 || text.count {it=='='}!=1)return null
        return runCatching {Parser(text.trim()).run()}.getOrNull()
    }
    fun latex(node:Formula):String=when(node) {
        is Formula.Atom -> if(node.italic)node.value else "\\mathrm{${node.value}}"
        is Formula.Join -> "${latex(node.left)}${if(node.symbol=="×")" \\times " else node.symbol}${latex(node.right)}"
        is Formula.Fraction -> "\\frac{${latex(node.numerator)}}{${latex(node.denominator)}}"
        is Formula.Root -> "\\sqrt{${latex(node.value)}}"
        is Formula.Power -> "{${latex(node.value)}}^{${latex(node.exponent)}}"
        is Formula.Index -> "{${latex(node.value)}}_{${latex(node.index)}}"
        is Formula.Group -> "\\left(${latex(node.value)}\\right)"
    }
    private class Parser(val input:String) {
        var at=0
        fun space(){while(at<input.length&&input[at].isWhitespace())at++}
        fun take(c:Char):Boolean {space();if(input.getOrNull(at)==c){at++;return true};return false}
        fun run():Formula {val left=sum();check(take('='));val right=sum();space();check(at==input.length);return Formula.Join(left,"=",right)}
        fun sum():Formula {
            var node=product()
            while(true){space();val c=input.getOrNull(at);if(c!='+'&&c!='-'&&c!='−')break;at++;node=Formula.Join(node,if(c=='+')"+" else "−",product())}
            return node
        }
        fun product():Formula {
            var node=power()
            while(true) {
                space();val c=input.getOrNull(at) ?: break
                when {
                    c=='/'||c=='÷' -> {at++;node=Formula.Fraction(node,power())}
                    c in "×*·.x" -> {at++;node=Formula.Join(node,"×",power())}
                    c=='('||c=='√'||c.isLetter() -> node=Formula.Join(node,"",power())
                    else -> return node
                }
            }
            return node
        }
        fun power():Formula {
            var node=atom();space()
            while(input.getOrNull(at) in listOf('²','³','^')) {
                val c=input[at++];node=Formula.Power(node,if(c=='^')atom() else Formula.Atom(if(c=='²')"2" else "3",false));space()
            }
            return node
        }
        fun atom():Formula {
            space();val c=input.getOrNull(at) ?: error("Missing operand")
            if(c=='√'){at++;val node=atom();return Formula.Root(if(node is Formula.Group)node.value else node)}
            if(c=='('){at++;val node=sum();check(take(')'));return Formula.Group(node)}
            if(c=='-'||c=='−'){at++;return Formula.Join(Formula.Atom("−",false),"",atom())}
            if(c.isDigit()) {
                val start=at;while(input.getOrNull(at)?.isDigit()==true)at++
                if(input.getOrNull(at) in listOf('.',',')&&input.getOrNull(at+1)?.isDigit()==true){at++;while(input.getOrNull(at)?.isDigit()==true)at++}
                return Formula.Atom(input.substring(start,at),false)
            }
            check(c in "UIRPWELSCZVAFXYTQHufrtlsωΩπλρτ")
            at++;var node:Formula=Formula.Atom(c.toString())
            val suffix=listOf("eff","max","min","moy").firstOrNull {input.startsWith(it,at)}
            if(suffix!=null){at+=suffix.length;node=Formula.Index(node,Formula.Atom(suffix,false))}
            else if(input.getOrNull(at)?.isDigit()==true){val start=at;while(input.getOrNull(at)?.isDigit()==true)at++;node=Formula.Index(node,Formula.Atom(input.substring(start,at),false))}
            return node
        }
    }
}
