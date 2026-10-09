package uk.co.siland.culvery.core.ui

/**
 * 4d-2 §7.1.3: reads Kotlin source the way the copy rules need. Comments (KDoc included) go; string literals are
 * collected where they start; the code inside `${…}` is read as code again, with its own literals, while `$name` stays
 * as text. Whether a literal is code's is decided by the calls it sits in, whatever line they start on.
 */
internal object CopyScan {
    data class Literal(val start: Int, val line: Int, val raw: String, val triple: Boolean)

    data class Finding(val path: String, val line: Int, val literal: String, val why: String)

    private val BANNED = Regex("""(?i)\b(master|kiosk|sync|read-only|assign|feed)(s|ed|ing)?\b""")

    // The name before a call's "(": a literal inside one of these is code's, not the household's.
    private val CODE_CALL = Regex(
        """(?:\bLog\.\w+|\btestTag|\brequire|\brequireNotNull|\bcheck|\bcheckNotNull|\bTODO|""" +
            """DateTimeFormatter\.ofPattern|\bRegex|\btoRegex|@\w+|\b\w*Exception)\s*$""",
    )

    // Kotlin's error(…), where a statement or an expression starts; Compose's semantics error(…) follows an `if (…)`.
    private val KOTLIN_ERROR = Regex("""(?:^|[\n=>{;]|throw|\?:)\s*error\s*$""")

    private val TAG_LINE = Regex("""\bTAG\b\s*=|^\s*@""")

    private class Tokens(val literals: List<Literal>, val masked: String)

    fun literals(source: String): List<Literal> = tokens(source).literals

    fun scan(path: String, source: String): List<Finding> {
        val tokens = tokens(source)
        val lines = source.lines()
        return tokens.literals.mapNotNull { literal ->
            if (TAG_LINE.containsMatchIn(lines[literal.line - 1]) || inCodeCall(tokens.masked, literal.start)) return@mapNotNull null
            val text = withoutTemplates(literal.raw)
            val identifier = text.none(Char::isWhitespace) && (text == text.lowercase() || text.any { it in "._/" })
            val why = listOfNotNull(
                BANNED.find(text)?.let { "says \"${it.value}\"" },
                "a straight apostrophe".takeIf { !identifier && '\'' in text },
                "a straight quote".takeIf { !identifier && ("\\\"" in literal.raw || (literal.triple && '"' in literal.raw)) },
            )
            if (why.isEmpty()) null else Finding(path, literal.line, text, why.joinToString(", "))
        }
    }

    fun withoutTemplates(raw: String): String = buildString {
        var j = 0
        while (j < raw.length) {
            if (raw.startsWith("\${", j)) j = braceEnd(raw, j + 2) else append(raw[j++])
        }
    }

    /** The literals, and the source with comments and literal bodies blanked (newlines kept) for reading the calls around them. */
    private fun tokens(s: String): Tokens {
        val out = mutableListOf<Literal>()
        val masked = StringBuilder(s)
        fun blank(from: Int, to: Int) {
            for (k in from until to) if (masked[k] != '\n') masked.setCharAt(k, ' ')
        }
        fun lineAt(i: Int): Int = 1 + (0 until i).count { s[it] == '\n' }
        fun read(from: Int, to: Int) {
            var i = from
            while (i < to) {
                when {
                    s.startsWith("//", i) -> {
                        val end = s.indexOf('\n', i).let { if (it < 0 || it > to) to else it }
                        blank(i, end)
                        i = end
                    }
                    s.startsWith("/*", i) -> {
                        val end = s.indexOf("*/", i + 2) + 2
                        blank(i, end)
                        i = end
                    }
                    s[i] == '"' -> {
                        val quotes = if (s.startsWith("\"\"\"", i)) 3 else 1
                        val close = "\"".repeat(quotes)
                        val templates = mutableListOf<IntRange>()
                        var j = i + quotes
                        while (!s.startsWith(close, j)) {
                            j = when {
                                quotes == 1 && s[j] == '\\' -> j + 2
                                s.startsWith("\${", j) -> braceEnd(s, j + 2).also { end -> templates += (j + 2) until (end - 1) }
                                else -> j + 1
                            }
                        }
                        out += Literal(i, lineAt(i), s.substring(i + quotes, j), triple = quotes == 3)
                        blank(i + quotes, j)
                        // The code inside ${…} is code again, with literals of its own.
                        templates.forEach { t ->
                            for (k in t) masked.setCharAt(k, s[k])
                            read(t.first, t.last + 1)
                        }
                        i = j + quotes
                    }
                    s[i] == '\'' -> i = s.indexOf('\'', if (s[i + 1] == '\\') i + 3 else i + 2) + 1
                    else -> i++
                }
            }
        }
        read(0, s.length)
        return Tokens(out.sortedBy { it.start }, masked.toString())
    }

    /** Whether any of the four calls around [start] is code's: a log, a tag, a check, a pattern, an annotation, an exception. */
    private fun inCodeCall(masked: String, start: Int): Boolean {
        var j = start - 1
        repeat(4) {
            var depth = 0
            while (j >= 0) {
                val c = masked[j]
                if (c in ")]}") {
                    depth++
                } else if (c in "([{") {
                    if (depth == 0) break
                    depth--
                }
                j--
            }
            if (j < 0) return false
            var head = masked.substring(0, j).trimEnd()
            // `require(x) { "…" }`: the call is the one before the lambda.
            if (masked[j] == '{' && head.endsWith(")")) head = head.substring(0, openerOf(head)).trimEnd()
            val code = when {
                head.endsWith("WriteRejectedException") -> false
                KOTLIN_ERROR.containsMatchIn(head) -> true
                else -> CODE_CALL.containsMatchIn(head)
            }
            if (code) return true
            j--
        }
        return false
    }

    private fun openerOf(s: String): Int {
        var depth = 0
        for (k in s.indices.reversed()) {
            when (s[k]) {
                ')' -> depth++
                '(' -> if (--depth == 0) return k
            }
        }
        return 0
    }

    private fun braceEnd(s: String, from: Int): Int {
        var depth = 1
        var j = from
        while (depth > 0) {
            when (s[j]) {
                '{' -> depth++
                '}' -> depth--
            }
            j++
        }
        return j
    }
}
