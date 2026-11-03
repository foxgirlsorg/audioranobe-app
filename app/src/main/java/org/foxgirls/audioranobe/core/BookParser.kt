package org.foxgirls.audioranobe.core

import android.net.Uri
import android.text.Html
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

data class BookChapter(val name: String, val text: String)

/** lib/bookImport: splits an .epub or .fb2 file into chapters (name + plain text, paragraphs on separate lines). */
object BookParser {
    fun parse(fileName: String, bytes: ByteArray): List<BookChapter> {
        val lower = fileName.lowercase()
        val chapters = when {
            lower.endsWith(".epub") -> epub(bytes)
            lower.endsWith(".fb2") -> fb2(bytes)
            else -> error("Поддерживаются файлы .epub и .fb2")
        }
        if (chapters.isEmpty()) error("В файле не нашлось глав с текстом")
        return chapters
    }

    fun withoutFirstLine(text: String): String = text.substringAfter('\n', "")

    private val SPACES = Regex("\\s+")
    private fun clean(s: String?): String = (s ?: "").replace(SPACES, " ").trim()

    private fun xml(text: String): Document {
        val f = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false; isValidating = false }
        runCatching { f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        val b = f.newDocumentBuilder().apply { setEntityResolver { _, _ -> InputSource(StringReader("")) } }
        return runCatching { b.parse(InputSource(StringReader(text.replaceFirst(Regex("^\\uFEFF?\\s*<\\?xml[^>]*\\?>"), "")))) }
            .getOrElse { error("Не удалось разобрать файл") }
    }

    /** Elements by local name, prefix or not (`opf:item` and `item` alike), in document order. */
    private fun Document.all(name: String): List<Element> = descendants(documentElement, name)
    private fun descendants(root: Element, vararg names: String): List<Element> {
        val out = ArrayList<Element>()
        fun walk(e: Element) {
            if (e.tagName.substringAfter(':') in names) out.add(e)
            val kids = e.childNodes
            for (i in 0 until kids.length) (kids.item(i) as? Element)?.let(::walk)
        }
        walk(root)
        return out
    }
    private fun Element.children(name: String): List<Element> {
        val kids = childNodes
        return (0 until kids.length).mapNotNull { kids.item(it) as? Element }.filter { it.tagName.substringAfter(':') == name }
    }

    private fun dirOf(path: String) = path.substring(0, path.lastIndexOf('/') + 1)
    private fun resolve(base: String, href: String): String {
        val out = ArrayList<String>()
        for (part in (base + Uri.decode(href.substringBefore('#'))).split('/')) {
            if (part == "..") out.removeLastOrNull() else if (part != "." && part.isNotEmpty()) out.add(part)
        }
        return out.joinToString("/")
    }

    private val BODY = Regex("<body[^>]*>(.*)</body>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val NOISE = Regex("<(script|style|head)[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val HEADING = Regex("<h[1-3][^>]*>(.*?)</h[1-3]>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val NAV = Regex("<nav([^>]*)>(.*?)</nav>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val LINK = Regex("<a[^>]*href=[\"']([^\"']*)[\"'][^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    private fun htmlText(html: String): String = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().replace("￼", "")

    /** A chapter page as paragraphs, one per line. */
    private fun htmlLines(html: String): List<String> {
        val body = (BODY.find(html)?.groupValues?.get(1) ?: html).replace(NOISE, "")
        return htmlText(body).split('\n').map(::clean).filter { it.isNotEmpty() }
    }

    private fun epub(bytes: ByteArray): List<BookChapter> {
        val files = HashMap<String, ByteArray>()
        runCatching {
            ZipInputStream(bytes.inputStream()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (!e.isDirectory) files[e.name.replace('\\', '/')] = z.readBytes()
                }
            }
        }
        if (files.isEmpty()) error("Файл не похож на EPUB")
        fun read(path: String) = files[path]?.toString(Charsets.UTF_8) ?: error("В EPUB нет файла $path")

        val opfPath = xml(read("META-INF/container.xml")).all("rootfile").firstOrNull()?.getAttribute("full-path")?.takeIf { it.isNotEmpty() }
            ?: error("В EPUB нет описания книги")
        val opf = xml(read(opfPath))
        val base = dirOf(opfPath)

        data class Item(val href: String, val type: String, val props: String)
        val manifest = opf.all("item").associate { it.getAttribute("id") to Item(it.getAttribute("href"), it.getAttribute("media-type"), it.getAttribute("properties")) }

        val labels = LinkedHashMap<String, String>()
        fun addLabel(tocBase: String, href: String?, label: String) {
            if (href.isNullOrEmpty() || label.isEmpty()) return
            labels.putIfAbsent(resolve(tocBase, href), label)
        }
        val navItem = manifest.values.firstOrNull { "nav" in it.props.split(' ') }
        val ncxItem = manifest.values.firstOrNull { it.type == "application/x-dtbncx+xml" }
        runCatching {
            if (navItem != null) {
                val navPath = resolve(base, navItem.href)
                val html = read(navPath)
                val toc = NAV.findAll(html).firstOrNull { "toc" in it.groupValues[1] }?.groupValues?.get(2) ?: html
                for (a in LINK.findAll(toc)) addLabel(dirOf(navPath), a.groupValues[1], clean(htmlText(a.groupValues[2])))
            } else if (ncxItem != null) {
                val ncxPath = resolve(base, ncxItem.href)
                for (point in xml(read(ncxPath)).all("navPoint")) {
                    val src = point.children("content").firstOrNull()?.getAttribute("src")
                    val label = point.children("navLabel").firstOrNull()?.children("text")?.firstOrNull()?.textContent
                    addLabel(dirOf(ncxPath), src, clean(label))
                }
            }
        }.onFailure { labels.clear() }

        val chapters = ArrayList<BookChapter>()
        for (ref in opf.all("itemref")) {
            val item = manifest[ref.getAttribute("idref")] ?: continue
            if (!Regex("x?html").containsMatchIn(item.type)) continue
            val path = resolve(base, item.href)
            val html = files[path]?.toString(Charsets.UTF_8) ?: continue
            val lines = htmlLines(html)
            if (lines.isEmpty()) continue
            val text = lines.joinToString("\n")
            val prev = chapters.lastOrNull()
            if (prev != null && labels.isNotEmpty() && path !in labels) {
                chapters[chapters.lastIndex] = prev.copy(text = prev.text + "\n" + text)
                continue
            }
            val heading = HEADING.find(html)?.groupValues?.get(1)?.let { clean(htmlText(it)) } ?: ""
            chapters.add(BookChapter(labels[path]?.takeIf { it.isNotEmpty() } ?: heading, text))
        }
        return chapters
    }

    private fun fb2(bytes: ByteArray): List<BookChapter> {
        val head = String(bytes, 0, minOf(200, bytes.size), Charsets.ISO_8859_1)
        val enc = Regex("encoding=[\"']([\\w-]+)[\"']", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1)
        val text = runCatching { String(bytes, charset(enc ?: "UTF-8")) }.getOrElse { bytes.toString(Charsets.UTF_8) }
        val doc = xml(text)
        val bodies = doc.all("body")
        val main = bodies.firstOrNull { !it.hasAttribute("name") } ?: bodies.firstOrNull() ?: error("В FB2 нет текста книги")

        fun leaf(sec: Element): BookChapter? {
            val lines = descendants(sec, "p", "subtitle", "v").map { clean(it.textContent) }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) return null
            val name = clean(sec.children("title").firstOrNull()?.let { t -> descendants(t, "p").joinToString(" ") { clean(it.textContent) } })
            return BookChapter(name, lines.joinToString("\n"))
        }
        val out = ArrayList<BookChapter>()
        fun walk(sec: Element) {
            val kids = sec.children("section")
            if (kids.isEmpty()) leaf(sec)?.let(out::add) else kids.forEach(::walk)
        }
        val sections = main.children("section")
        if (sections.isEmpty()) leaf(main)?.let(out::add) else sections.forEach(::walk)
        return out
    }
}
