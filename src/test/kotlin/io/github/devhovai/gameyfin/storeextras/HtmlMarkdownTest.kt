package io.github.devhovai.gameyfin.storeextras

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HtmlMarkdownTest {
    private fun convert(html: String) = HtmlMarkdown.convert(html)

    private fun steamDescription(file: String, appId: Int): String =
        parseObject(fixture(file).body)!![appId.toString()].obj()!!["data"].obj()!!["detailed_description"].str()!!

    @Test fun `paragraphs, headings and lists of a Steam page become Markdown`() {
        val md = convert(steamDescription("steam_details_zomboid.json", 108600))
        assertTrue(md.startsWith("Project Zomboid is an open-ended zombie-infested sandbox. It asks one simple question – how will you die?\n\n"))
        assertTrue("\n\n## Current Features\n\n- Hardcore Sandbox Zombie Survival Game with a focus on realistic survival.\n- Online multiplayer" in md, md)
        assertFalse("<" in md.replace("\\<", ""), md)
        assertFalse("\n\n\n" in md)
    }

    @Test fun `videos are dropped and images and line breaks survive`() {
        val md = convert(steamDescription("steam_details_bg3.json", 1086940))
        assertFalse("video" in md || "webm" in md || ".mp4" in md, md)
        assertTrue(md.startsWith("Gather your party and return to the Forgotten Realms"), md)
        assertTrue("Dungeons & Dragons." in md)
        assertTrue(Regex("""\n\n!\[]\(https://shared\.akamai\.steamstatic\.com/[^)\s]+\)""").containsMatchIn(md), md)
    }

    @Test fun `plain br text keeps hard line breaks and decodes entities`() {
        val md = convert(steamDescription("steam_details_anno1503.json", 2820110))
        assertTrue("Treasures, Monsters & Pirates" in md)
        assertTrue("\n\nYour cities look better than ever, thanks to resolutions all the way up to 4k.  \nDiscover Anno 1503’s" in md, md)
    }

    @Test fun `GOG style double br is a paragraph break`() {
        assertEquals("One<b>\n\nTwo", convert("One&lt;b&gt;<br><br>Two").replace("\\<", "<").replace("\\>", ">"))
    }

    @Test fun `nested and ordered lists are indented`() {
        val md = convert("<ul><li>a<ul><li>b</li><li>c</li></ul></li><li>d</li></ul><ol><li>x</li><li>y</li></ol>")
        assertEquals("- a\n  - b\n  - c\n- d\n\n1. x\n2. y", md)
    }

    @Test fun `links keep their target, Steam link filter is unwrapped, relative links lose the link`() {
        assertEquals(
            "See [the wiki](https://example.org/a%20b) and [forum](https://example.org/x?y=1&z=2) or just text.",
            convert("""See <a class="bb_link" href="https://steamcommunity.com/linkfilter/?u=https%3A%2F%2Fexample.org%2Fa%20b" target="_blank">the wiki</a> and <a href='https://example.org/x?y=1&amp;z=2'>forum</a> or <a href="/relative">just text</a>."""),
        )
    }

    @Test fun `emphasis markers hug the text`() {
        assertEquals("**bold** and *it* x", convert("<b>bold </b>and <i> it</i> x"))
    }

    @Test fun `Markdown syntax in plain text is escaped`() {
        assertEquals("2 \\* 3 = 6 and snake\\_case \\[x\\] \\<tag\\>", convert("2 * 3 = 6 and snake_case [x] &lt;tag&gt;"))
        assertEquals("\\# not a heading", convert("# not a heading"))
        assertEquals("1\\. not a list", convert("1. not a list"))
    }

    @Test fun `scripts, styles and unknown tags do not leak, broken HTML does not throw`() {
        assertEquals("Hello world", convert("<style>p{}</style>Hello <script>alert(1)</script><blink>world</blink><b>"))
        assertEquals("", convert("<video src='x'></video>"))
        assertEquals("a", convert("<p>a<p><div>"))
    }

    @Test fun `headings are capped to levels 2 to 4 and lose inner emphasis`() {
        assertEquals("## Big\n\ntext\n\n#### Small", convert("<h1><strong>Big</strong></h1>text<h5>Small</h5>"))
    }
}
