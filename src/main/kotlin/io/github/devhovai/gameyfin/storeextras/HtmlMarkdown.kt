package io.github.devhovai.gameyfin.storeextras

/**
 * Converts the HTML snippets stores deliver as game descriptions (Steam `detailed_description`, GOG `description.full`)
 * into Markdown that Gameyfin renders with react-markdown: no raw HTML, line breaks via remark-breaks.
 *
 * Keeps paragraphs, headings, lists, emphasis, links and images; drops videos, scripts and everything else it does
 * not know. Tolerant of unclosed and stray tags, because store HTML is hand-written.
 */
object HtmlMarkdown {
    private val token = Regex("""<!--.*?-->|<(/?)([a-zA-Z][a-zA-Z0-9]*)((?:"[^"]*"|'[^']*'|[^>"'])*)>""", RegexOption.DOT_MATCHES_ALL)
    private val attribute = Regex("""([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*(?:=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+)))?""")
    private val skipped = setOf("video", "audio", "script", "style", "iframe", "noscript", "object", "embed", "svg", "head", "button", "form")
    private val blocks = setOf("p", "div", "section", "article", "header", "footer", "blockquote", "pre", "center", "figure", "figcaption")
    private val voids = setOf("br", "hr", "img", "source", "input", "meta", "link", "wbr")
    private val lineStartMarker = Regex("""^(#{1,6}(\s|$)|[-+](\s|$)|\d+[.)](\s|$))""")
    private val listMarkerLine = Regex("""\s*(-|\d+\.) """)
    private val entities = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to "\u00A0", "copy" to "©", "reg" to "®",
        "trade" to "™", "hellip" to "…", "mdash" to "—", "ndash" to "–", "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“",
        "rdquo" to "”", "bull" to "•", "middot" to "·", "euro" to "€", "times" to "×", "deg" to "°",
    )

    /** Returns Markdown, or an empty string when nothing readable is left. */
    fun convert(html: String): String = Converter().run(html)

    internal fun decodeEntities(text: String): String =
        Regex("""&(#[xX][0-9a-fA-F]+|#\d+|[a-zA-Z]+);""").replace(text) { m ->
            val name = m.groupValues[1]
            when {
                name.startsWith("#x") || name.startsWith("#X") -> name.drop(2).toIntOrNull(16)?.let(::codePoint)
                name.startsWith("#") -> name.drop(1).toIntOrNull()?.let(::codePoint)
                else -> entities[name]
            } ?: m.value
        }

    private fun codePoint(cp: Int): String? = if (cp in 1..0x10FFFF) String(Character.toChars(cp)) else null

    private fun escape(text: String): String {
        val sb = StringBuilder(text.length + 8)
        for (c in text) {
            if (c in "\\`*_[]<>~|") sb.append('\\')
            sb.append(c)
        }
        return sb.toString()
    }

    /** Percent-encodes what would end a Markdown link destination early. */
    private fun destination(url: String): String =
        url.trim().replace(" ", "%20").replace("(", "%28").replace(")", "%29").replace("<", "%3C").replace(">", "%3E")

    /** Steam wraps outgoing links in its link filter; the plain target is what we want to link to. */
    private fun unwrapLinkFilter(href: String): String {
        val m = Regex("""^https?://steamcommunity\.com/linkfilter/\?(?:u|url)=(.+)$""").find(href) ?: return href
        return runCatching { java.net.URLDecoder.decode(m.groupValues[1], Charsets.UTF_8) }.getOrDefault(href)
    }

    private fun attrs(raw: String): Map<String, String> =
        attribute.findAll(raw).associate { m ->
            m.groupValues[1].lowercase() to decodeEntities(m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } })
        }

    private class ListState(val ordered: Boolean) {
        var counter = 0
        var markerWidth = 2
    }

    private class Converter {
        private val out = StringBuilder()
        private val lists = ArrayDeque<ListState>()
        private val inlineStarts = ArrayDeque<Triple<String, Int, String>>()
        private var skipTag: String? = null
        private var skipDepth = 0
        private var headingStart = -1
        private var linkDepth = 0

        fun run(html: String): String {
            var pos = 0
            for (m in token.findAll(html)) {
                if (m.range.first > pos) text(html.substring(pos, m.range.first))
                pos = m.range.last + 1
                if (m.value.startsWith("<!--")) continue
                tag(m.groupValues[2].lowercase(), m.groupValues[1].isNotEmpty(), m.groupValues[3])
            }
            if (pos < html.length) text(html.substring(pos))
            while (inlineStarts.isNotEmpty()) closeInline(inlineStarts.last().first)
            return tidy(out.toString())
        }

        private fun text(raw: String) {
            if (skipTag != null) return
            var t = decodeEntities(raw).replace(Regex("""[\s\u00A0]+"""), " ")
            if (atLineStartIgnoringIndent() || out.endsWith(" ")) t = t.trimStart()
            if (t.isEmpty()) return
            val escaped = escape(t)
            val marker = if (atLineStartIgnoringIndent()) lineStartMarker.find(escaped) else null
            out.append(
                when {
                    marker == null -> escaped
                    escaped[0].isDigit() -> escaped.replaceFirst(Regex("""^(\d+)([.)])"""), "$1\\\\$2")
                    else -> "\\" + escaped
                }
            )
        }

        private fun atLineStartIgnoringIndent(): Boolean {
            val lineStart = out.lastIndexOf("\n") + 1
            return out.substring(lineStart).isBlank()
        }

        private fun tag(name: String, closing: Boolean, rawAttrs: String) {
            val skip = skipTag
            if (skip != null) {
                if (name == skip && name !in voids) {
                    if (closing) { if (--skipDepth == 0) skipTag = null } else skipDepth++
                }
                return
            }
            if (!closing && name in skipped) {
                skipTag = name; skipDepth = 1
                return
            }
            when {
                name == "br" -> lineBreak()
                name == "hr" -> { blockBreak(); out.append("---"); blockBreak() }
                name == "img" && !closing -> image(attrs(rawAttrs))
                name in blocks -> blockBreak()
                name.length == 2 && name[0] == 'h' && name[1] in '1'..'6' -> heading(name[1] - '0', closing)
                name == "ul" || name == "ol" -> list(name == "ol", closing)
                name == "li" -> if (!closing) listItem()
                name == "strong" || name == "b" -> inline(name, closing, "**")
                name == "em" || name == "i" -> inline(name, closing, "*")
                name == "code" -> inline(name, closing, "`")
                name == "u" || name == "span" || name == "font" -> Unit
                name == "a" -> link(closing, attrs(rawAttrs)["href"])
                name == "tr" -> lineBreak()
                name == "td" || name == "th" -> if (!closing && out.isNotEmpty() && !out.endsWith("\n")) out.append(" | ")
                else -> Unit
            }
        }

        private fun endsWithBlankLine() = out.endsWith("\n\n")

        private fun blockBreak() {
            if (out.isBlank()) { out.setLength(0); return }
            if (lists.isNotEmpty() && onListMarker()) return // <li><p>text</p></li>: the text stays on the marker's line
            while (out.endsWith(" ")) out.setLength(out.length - 1)
            if (lists.isNotEmpty()) {
                if (!out.endsWith("\n")) out.append('\n')
            } else {
                while (out.endsWith("\n")) out.setLength(out.length - 1)
                out.append("\n\n")
            }
        }

        private fun onListMarker(): Boolean = listMarkerLine.matches(out.substring(out.lastIndexOf("\n") + 1))

        private fun lineBreak() {
            if (out.isBlank() || out.endsWith("\n\n")) return
            if (out.endsWith("  \n")) {
                out.setLength(out.length - 3)
                blockBreak()
                return
            }
            if (out.endsWith("\n")) return
            out.append("  \n")
            if (lists.isNotEmpty()) out.append(" ".repeat(indent() + lists.last().markerWidth))
        }

        private fun indent(): Int = lists.dropLast(1).sumOf { it.markerWidth }

        private fun heading(level: Int, closing: Boolean) {
            if (!closing) {
                blockBreak()
                out.append("#".repeat(level.coerceIn(2, 4))).append(' ')
                headingStart = out.length
            } else {
                if (headingStart >= 0) {
                    val body = out.substring(headingStart).replace(Regex("""\s+"""), " ").trim()
                    out.setLength(headingStart)
                    if (body.isEmpty()) out.setLength(out.lastIndexOf("\n") + 1) else out.append(body)
                    headingStart = -1
                }
                blockBreak()
            }
        }

        private fun list(ordered: Boolean, closing: Boolean) {
            if (!closing) {
                if (lists.isEmpty()) blockBreak() else if (!out.endsWith("\n")) out.append('\n')
                lists.addLast(ListState(ordered))
            } else if (lists.isNotEmpty()) {
                lists.removeLast()
                if (lists.isEmpty()) blockBreak() else if (!out.endsWith("\n")) out.append('\n')
            }
        }

        private fun listItem() {
            val state = lists.lastOrNull() ?: ListState(false).also { lists.addLast(it) }
            if (!out.endsWith("\n") && out.isNotEmpty()) out.append('\n')
            if (out.endsWith("  \n")) { out.setLength(out.length - 3); out.append('\n') }
            state.counter++
            val marker = if (state.ordered) "${state.counter}. " else "- "
            state.markerWidth = marker.length
            out.append(" ".repeat(indent())).append(marker)
        }

        private fun inline(name: String, closing: Boolean, marker: String) {
            if (headingStart >= 0) return // headings are bold already
            if (!closing) inlineStarts.addLast(Triple(name, out.length, marker)) else closeInline(name)
        }

        private fun closeInline(name: String) {
            val index = inlineStarts.indexOfLast { it.first == name }
            if (index < 0) return
            val (_, start, marker) = inlineStarts.removeAt(index)
            val inner = out.substring(minOf(start, out.length))
            out.setLength(minOf(start, out.length))
            if (inner.isBlank()) { out.append(inner); return }
            val lead = inner.takeWhile { it == ' ' }
            val trail = inner.takeLastWhile { it == ' ' }
            val core = inner.trim(' ')
            out.append(lead).append(marker).append(core).append(marker).append(trail)
        }

        private fun link(closing: Boolean, href: String?) {
            if (!closing) {
                val target = href?.let(::unwrapLinkFilter)?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                inlineStarts.addLast(Triple("a", out.length, target ?: ""))
                linkDepth++
                return
            }
            val index = inlineStarts.indexOfLast { it.first == "a" }
            if (index < 0) return
            val (_, start, target) = inlineStarts.removeAt(index)
            linkDepth--
            val inner = out.substring(minOf(start, out.length))
            out.setLength(minOf(start, out.length))
            if (inner.isBlank() || target.isEmpty()) { out.append(inner); return }
            val lead = inner.takeWhile { it == ' ' }
            val trail = inner.takeLastWhile { it == ' ' }
            out.append(lead).append('[').append(inner.trim(' ')).append("](").append(destination(target)).append(')').append(trail)
        }

        private fun image(attrs: Map<String, String>) {
            val src = (attrs["src"] ?: attrs["data-src"])?.trim()?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return
            val alt = (attrs["alt"] ?: "").replace(Regex("""[\[\]\s]+"""), " ").trim()
            val standalone = linkDepth == 0 && lists.isEmpty()
            if (standalone) blockBreak()
            out.append("![").append(alt).append("](").append(destination(src)).append(')')
            if (standalone) blockBreak()
        }

        /** Trims trailing blanks per line (keeping the two-space hard break), collapses blank runs, drops outer blanks. */
        private fun tidy(markdown: String): String {
            val lines = markdown.replace("\r", "").split('\n').map { line ->
                if (line.isBlank()) "" else if (line.endsWith("  ")) line.trimEnd() + "  " else line.trimEnd()
            }
            val sb = StringBuilder()
            var blank = 0
            for ((i, line) in lines.withIndex()) {
                if (line.isEmpty()) { blank++; continue }
                if (sb.isNotEmpty()) {
                    // a hard break right before a blank line or the end is just a paragraph end
                    if (blank > 0 && sb.endsWith("  ")) sb.setLength(sb.trimEnd().length)
                    sb.append(if (blank > 0) "\n\n" else "\n")
                }
                blank = 0
                sb.append(line)
                if (i == lines.lastIndex) sb.setLength(sb.trimEnd().length)
            }
            return sb.toString().trimEnd()
        }
    }
}
