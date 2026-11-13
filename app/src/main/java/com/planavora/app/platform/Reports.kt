package com.planavora.app.platform

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.planavora.app.R
import com.planavora.app.domain.*
import com.planavora.app.ui.label
import com.planavora.app.ui.localDate
import com.planavora.app.ui.localTime
import java.io.OutputStream
import java.util.Locale

data class Report(val title: String, val heading: String, val rows: List<Pair<String, String>>) {
    fun text() = (listOf(title, heading) + rows.map { "${it.first}  •  ${it.second}" }).joinToString("\n")

    fun writeCsv(output: OutputStream, context: Context) {
        fun cell(raw: String): String {
            val safe = if (raw.trimStart().firstOrNull() in listOf('=', '+', '-', '@') || raw.firstOrNull() in listOf('\t', '\n', '\r')) "'$raw" else raw
            return "\""+safe.replace("\"", "\"\"")+"\""
        }
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("\uFEFF")
            val header = context.getString(R.string.name) to context.getString(R.string.details)
            for ((name, detail) in listOf(header, title to heading) + rows) {
                writer.write(cell(name) + "," + cell(detail) + "\r\n")
            }
        }
    }

    fun writePdf(output: OutputStream) {
        val document = PdfDocument()
        try {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(48, 39, 52); textSize = 12f }
            val header = TextPaint(paint).apply { textSize = 24f; typeface = Typeface.create("serif", Typeface.NORMAL) }
            var pageNumber = 0
            var y = 0
            var current = document.startPage(PdfDocument.PageInfo.Builder(595, 842, ++pageNumber).create())
            fun draw(canvas: Canvas, layout: StaticLayout, line: Int) {
                canvas.save()
                canvas.translate(42f, (y - layout.getLineTop(line)).toFloat())
                canvas.clipRect(0, layout.getLineTop(line), 511, layout.getLineBottom(line))
                layout.draw(canvas)
                canvas.restore()
                y += layout.getLineBottom(line) - layout.getLineTop(line)
            }
            fun block(text: String, style: TextPaint) {
                val layout = StaticLayout.Builder.obtain(text, 0, text.length, style, 511)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(3f, 1f).build()
                for (line in 0 until layout.lineCount) {
                    if (y + layout.getLineBottom(line) - layout.getLineTop(line) > 797) {
                        document.finishPage(current)
                        current = document.startPage(PdfDocument.PageInfo.Builder(595, 842, ++pageNumber).create())
                        y = 42
                    }
                    draw(current.canvas, layout, line)
                }
                y += 8
            }
            y = 42
            block(title, header)
            block(heading, paint)
            rows.forEach { (name, detail) -> block("$name  •  $detail", paint) }
            document.finishPage(current)
            document.writeTo(output)
        } finally { document.close() }
    }
}

fun buildReport(context: Context, title: String, event: EventDetails, items: List<Item>, section: Section?, sensitive: Boolean): Report {
    val locale: Locale = context.resources.configuration.locales[0]
    val money = Money(locale, event.currency)
    val totals = budget(event, items)
    val names = items.associate { it.id to it.title }
    fun text(id: Int) = context.getString(id)
    val rows = buildList {
        if (section == null || section == Section.EXPENSE) {
            add(text(R.string.budget) to money.display(event.budget))
            add(text(R.string.planned) to money.display(totals.planned))
            add(text(R.string.paid_amount) to money.display(totals.spent))
            add(text(R.string.remaining) to money.display(totals.remaining))
            add(text(R.string.vendor_balance) to money.display(totals.vendorBalance))
        }
        for (item in items.filter { section == null || it.section == section }) {
            val details = when (val d = item.detail) {
                is Details.Task -> listOf(d.category, if (d.complete) text(R.string.complete) else text(R.string.pending), localDate(d.due, locale))
                is Details.Guest -> listOf(text(d.rsvp.label), d.headcount.toString(), names[d.groupId].orEmpty(),
                    d.contact.takeIf { sensitive }.orEmpty(), d.dietary.takeIf { sensitive }.orEmpty())
                is Details.Expense -> listOf(d.category, money.display(d.planned), money.display(d.paid), localDate(d.due, locale))
                is Details.Vendor -> listOf(d.business, d.category, money.display(d.agreed))
                is Details.Payment -> listOf(names[d.vendorId].orEmpty(), money.display(d.amount),
                    text(if (d.paid) R.string.paid else R.string.pending), localDate(d.due, locale))
                is Details.Timeline -> listOf(localTime(d.start, locale), localTime(d.end, locale), d.location)
                is Details.Seating -> listOf(d.capacity.toString()) + items.filter { (it.detail as? Details.Guest)?.groupId == item.id }.map(Item::title)
                is Details.Document -> emptyList()
            }
            add(item.title to details.filter(String::isNotBlank).joinToString(" · "))
        }
    }
    return Report(title, listOf(section?.let { text(it.label) } ?: text(R.string.event_summary),
        localDate(event.date, locale), event.location).filter(String::isNotBlank).joinToString(" · "), rows)
}
