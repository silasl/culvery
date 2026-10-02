import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * D8: what a release build logs. Every Log.w, Log.e, Log.wtf and Log.println in shipped code passes a tag and one
 * message, never a throwable, and the message interpolates only a type, an HTTP code, or a qualified id in
 * [LogHygiene.ALLOWED].
 */
class LogHygieneTest {
    // build-logic/convention's tests run in that directory.
    private val repo = File("../..").canonicalFile

    @Test
    fun shippedWarningsAndErrorsCarryNoThrowableAndNoPersonalData() {
        val problems = shippedSources().flatMap { file ->
            LogHygiene.problems(file.readText()).map { "${file.relativeTo(repo)}: $it" }
        }
        assertWithMessage(problems.joinToString("\n")).that(problems).isEmpty()
    }

    @Test
    fun aThrowableArgumentIsCaught() {
        assertThat(LogHygiene.problems("""Log.w(TAG, "Couldn't save", e)""")).hasSize(1)
        assertThat(LogHygiene.problems("""Log.wtf(TAG, "Couldn't save", e)""")).hasSize(1)
    }

    @Test
    fun anInterpolatedNameOrABareLocalIsCaught() {
        assertThat(LogHygiene.problems("""Log.e(TAG, "Couldn't add ${'$'}{person.name}")""")).hasSize(1)
        assertThat(LogHygiene.problems("""Log.e(TAG, "Couldn't read ${'$'}id")""")).hasSize(1)
        assertThat(LogHygiene.problems("""Log.println(Log.WARN, TAG, "Couldn't read ${'$'}email")""")).hasSize(1)
    }

    @Test
    fun aMessageThatIsNotAStringIsCaught() {
        assertThat(LogHygiene.problems("""Log.w(TAG, message)""")).hasSize(1)
    }

    @Test
    fun aTypeACodeAndAnAllowedIdPassAndDebugLinesAreNotChecked() {
        val source = """
            Log.w(TAG, "${'$'}{conn.id}: answered ${'$'}{answer.code} (${'$'}{e::class.simpleName})")
            Log.e(TAG, "first part " +
                "second (${'$'}{it::class.simpleName})")
            Log.println(Log.WARN, TAG, "${'$'}{conn.id}: fixed words")
            Log.d(TAG, "stripped from release: ${'$'}{person.name}", e)
        """.trimIndent()
        assertThat(LogHygiene.problems(source)).isEmpty()
    }

    private fun shippedSources(): List<File> = repo.walkTopDown()
        .onEnter { it.name !in setOf("build", ".gradle", ".git", "build-logic") }
        .filter { it.isFile && it.extension == "kt" }
        .filter { f -> SHIPPED.any { "${File.separator}src${File.separator}$it${File.separator}" in f.path } }
        .toList()

    private companion object {
        /** Source sets that reach a release build. */
        val SHIPPED = listOf("main", "release")
    }
}

/** Finds the Log.w, Log.e, Log.wtf and Log.println calls in Kotlin source and says what is wrong with each. */
internal object LogHygiene {
    /**
     * Qualified ids and fixed words a message may interpolate (plan review 10: never a bare local, whose meaning the
     * line can't show): never a name, email, place, PIN, coordinates or zone.
     */
    val ALLOWED = setOf(
        "cap.id", "conn.id", "connection.id", "connection.providerId", "change.connectionId", "change.kind", "it.descriptor.id",
        "step.id", "stored.connection.id", "this.id", "this.kind", "kind.name", "this.failedDrains", "this.reason",
        "answer.reason", "what.label", "request.method", "pathTemplate(request.url)", "grant.scopes.size", "CALENDAR_SCOPES.size",
    )

    private val CALL = Regex("""\bLog\.(w|e|wtf|println)\(""")
    private val TEMPLATE = Regex("""\$\{([^}]*)\}|\$([A-Za-z_][A-Za-z0-9_]*)""")

    fun problems(source: String): List<String> = CALL.findAll(source).mapNotNull { match ->
        val line = source.substring(0, match.range.first).count { it == '\n' } + 1
        val args = arguments(source, match.range.last + 1)
        // Log.println(priority, tag, message): the same rule after its priority.
        problem(if (match.groupValues[1] == "println") args.drop(1) else args)?.let { "line $line: $it" }
    }.toList()

    private fun problem(args: List<String>): String? {
        if (args.size != 2) return "passes ${args.size} arguments after any priority: a tag and one message only, never a throwable"
        val message = args[1]
        val literals = literals(message)
        if (literals.isEmpty() || message.without(literals).any { it != '+' && !it.isWhitespace() }) {
            return "the message must be string literals"
        }
        val bad = literals
            .flatMap { r -> TEMPLATE.findAll(message.substring(r)).map { it.groupValues[1].ifEmpty { it.groupValues[2] } } }
            .filterNot(::allowed)
        return if (bad.isEmpty()) null else "interpolates ${bad.joinToString()}: only ::class.simpleName, a .code or a qualified id in LogHygiene.ALLOWED"
    }

    private fun allowed(expression: String): Boolean = expression.trim().let {
        it.endsWith("::class.simpleName") || it.endsWith(".code") || it in ALLOWED
    }

    /** The top-level arguments of the call whose "(" ends just before [from]. */
    private fun arguments(source: String, from: Int): List<String> {
        val args = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var i = from
        while (i < source.length) {
            val c = source[i]
            if (c == '"') {
                val end = stringEnd(source, i)
                current.append(source, i, end)
                i = end
                continue
            }
            when (c) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> if (depth == 0) {
                    args += current.toString().trim()
                    return args
                } else {
                    depth--
                }
                ',' -> if (depth == 0) {
                    args += current.toString().trim()
                    current.clear()
                    i++
                    continue
                }
            }
            current.append(c)
            i++
        }
        error("A Log call that never closes")
    }

    /** Where the string literals in [text] are. */
    private fun literals(text: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var i = 0
        while (i < text.length) {
            if (text[i] == '"') {
                val end = stringEnd(text, i)
                ranges += i until end
                i = end
            } else {
                i++
            }
        }
        return ranges
    }

    /** Just past the string literal that starts at [start], plain or raw, stepping over `${…}` templates. */
    private fun stringEnd(text: String, start: Int): Int {
        val raw = text.startsWith("\"\"\"", start)
        var i = start + if (raw) 3 else 1
        while (i < text.length) {
            when {
                !raw && text[i] == '\\' -> i += 2
                text.startsWith("\${", i) -> {
                    var depth = 1
                    i += 2
                    while (depth > 0) {
                        when (text[i]) {
                            '{' -> depth++
                            '}' -> depth--
                            '"' -> i = stringEnd(text, i) - 1
                        }
                        i++
                    }
                }
                raw && text.startsWith("\"\"\"", i) -> return i + 3
                !raw && text[i] == '"' -> return i + 1
                else -> i++
            }
        }
        error("A string that never closes")
    }

    private fun String.without(ranges: List<IntRange>): String {
        val out = StringBuilder()
        var from = 0
        ranges.forEach { r ->
            out.append(this, from, r.first)
            from = r.last + 1
        }
        out.append(this, from, length)
        return out.toString()
    }
}
