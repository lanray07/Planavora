package com.planavora.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource as s
import androidx.compose.ui.unit.dp
import com.planavora.app.R
import com.planavora.app.domain.*
import com.planavora.app.platform.LocalFiles
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Records(section: Section, rows: List<Item>, event: EventDetails, locale: Locale, money: Money,
            add: () -> Unit, edit: (Item) -> Unit, show: (Section) -> Unit, history: (Item) -> Unit,
            remove: (Item) -> Unit, change: (Item, Details) -> Unit) {
    val context = LocalContext.current
    var filter by rememberSaveable { mutableStateOf(R.string.all) }
    var sort by rememberSaveable { mutableStateOf(R.string.due_date) }
    var fileError by remember { mutableStateOf(false) }
    val all = rows.filter { it.section == section }
    val names = rows.associate { it.id to it.title }
    val conflicts = remember(rows) { if (section == Section.TIMELINE) timelineConflicts(rows) else emptySet() }
    val records = when {
        section == Section.TASK -> all.filter {
            val task = it.detail as Details.Task
            filter == R.string.all || task.complete == (filter == R.string.complete)
        }.let { result ->
            when (sort) {
                R.string.priority -> result.sortedByDescending { (it.detail as Details.Task).priority }
                R.string.name -> result.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
                else -> result.sortedBy { dueDate(it.detail) ?: LocalDate.MAX }
            }
        }
        section == Section.GUEST -> all.filter { filter == R.string.all || (it.detail as Details.Guest).rsvp.label == filter }
        section == Section.TIMELINE -> all.sortedBy { (it.detail as Details.Timeline).start }
        else -> all
    }
    val noGuestTracking = section == Section.GUEST && !event.guestTracking
    LazyColumn(Modifier.widthIn(max = 1150.dp).fillMaxSize(), contentPadding = PaddingValues(20.dp, 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(enabled = !noGuestTracking && (section != Section.PAYMENT || rows.any { it.section == Section.VENDOR }), onClick = add) {
                    Text(s(R.string.add_record))
                }
                val related = when (section) {
                    Section.TASK -> listOf(Section.TIMELINE)
                    Section.GUEST -> listOf(Section.SEATING)
                    Section.EXPENSE -> listOf(Section.VENDOR, Section.PAYMENT)
                    Section.PAYMENT -> listOf(Section.VENDOR, Section.EXPENSE)
                    else -> emptyList()
                }
                related.forEach { OutlinedButton(onClick = { show(it) }) { Text(s(it.label)) } }
            }
        }
        if (section == Section.PAYMENT && rows.none { it.section == Section.VENDOR }) item { Text(s(R.string.vendor_first)) }
        if (noGuestTracking) item { Text(s(R.string.guest_disabled)) }
        if (section == Section.EXPENSE) item { BudgetOverview(event, rows, money) }
        if (section == Section.TASK || section == Section.GUEST) item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val options = if (section == Section.TASK) listOf(R.string.all, R.string.pending, R.string.complete)
                    else listOf(R.string.all) + Rsvp.entries.map { it.label }
                Choice(s(R.string.filter), filter, options, context::getString) { filter = it }
                if (section == Section.TASK) Choice(s(R.string.sort), sort,
                    listOf(R.string.due_date, R.string.priority, R.string.name), context::getString) { sort = it }
            }
        }
        if (section == Section.SEATING) item {
            val unassigned = rows.filter { (it.detail as? Details.Guest)?.groupId == "" }.map(Item::title)
            if (unassigned.isNotEmpty()) Panel {
                Text(s(R.string.unassigned), style = MaterialTheme.typography.titleMedium)
                Text(unassigned.joinToString(", "))
                TextButton(onClick = { show(Section.GUEST) }) { Text(s(R.string.guests)) }
            }
        }
        if (records.isEmpty()) item { Panel { Text(s(R.string.no_records)) } }
        items(records, key = Item::id) { record ->
            Panel {
                Text(record.title, style = MaterialTheme.typography.titleLarge)
                val fields = when (val d = record.detail) {
                    is Details.Task -> listOf(d.category, localDate(d.due, locale), s(if (d.complete) R.string.complete else R.string.pending))
                    is Details.Guest -> listOf(d.household, s(if (d.invited) R.string.invited else R.string.not_invited), s(d.rsvp.label),
                        s(R.string.choice_value, s(R.string.headcount), NumberFormat.getIntegerInstance(locale).format(d.headcount)), names[d.groupId].orEmpty())
                    is Details.Expense -> listOf(d.category, names[d.vendorId].orEmpty(),
                        s(R.string.choice_value, s(R.string.paid_amount), money.display(d.paid)),
                        s(R.string.choice_value, s(R.string.remaining), money.display((d.committed - d.paid).coerceAtLeast(0))),
                        s(if (d.cancelled) R.string.cancelled else if (d.committed > 0 && d.paid >= d.committed) R.string.paid
                            else if (d.paid > 0) R.string.part_paid else R.string.planned), localDate(d.due, locale))
                    is Details.Vendor -> listOf(d.business, d.category, s(if (d.booked) R.string.booked else R.string.pending),
                        s(R.string.choice_value, s(R.string.quote), money.display(d.quote)),
                        s(R.string.choice_value, s(R.string.agreed), money.display(d.agreed)))
                    is Details.Payment -> listOf(names[d.vendorId].orEmpty(), money.display(d.amount), localDate(d.due, locale),
                        s(if (d.paid) R.string.paid else R.string.pending), if (d.deposit) s(R.string.deposit) else "")
                    is Details.Timeline -> listOf(localTime(d.start, locale) + "–" + localTime(d.end, locale), d.person, d.location,
                        names[d.vendorId].orEmpty(), if (d.complete) s(R.string.complete) else "")
                    is Details.Seating -> listOf(s(R.string.choice_value, s(R.string.capacity), if (d.capacity == 0) s(R.string.none) else
                        NumberFormat.getIntegerInstance(locale).format(d.capacity)),
                        rows.filter { (it.detail as? Details.Guest)?.groupId == record.id }.joinToString(", ") { it.title })
                    is Details.Document -> emptyList()
                }
                val caption = fields.filter(String::isNotBlank).joinToString(" · ")
                if (caption.isNotBlank()) Text(caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (record.id in conflicts) Text(s(R.string.time_conflict), color = MaterialTheme.colorScheme.error)
                if (dueDate(record.detail)?.isBefore(LocalDate.now()) == true) Text(s(R.string.overdue), color = MaterialTheme.colorScheme.error)
                val notes = record.detail.notes
                if (notes.isNotBlank()) Text(notes, maxLines = 4)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (val d = record.detail) {
                        is Details.Task -> TextButton(onClick = { change(record, d.copy(complete = !d.complete)) }) { Text(s(if (d.complete) R.string.undo else R.string.complete)) }
                        is Details.Guest -> Choice(s(R.string.rsvp), d.rsvp, Rsvp.entries, { context.getString(it.label) }) { change(record, d.copy(rsvp = it)) }
                        is Details.Timeline -> TextButton(onClick = { change(record, d.copy(complete = !d.complete)) }) { Text(s(if (d.complete) R.string.undo else R.string.complete)) }
                        else -> Unit
                    }
                    TextButton(onClick = { edit(record) }) { Text(s(R.string.edit)) }
                    if (section in listOf(Section.TASK, Section.GUEST, Section.PAYMENT)) TextButton(onClick = { history(record) }) { Text(s(R.string.history)) }
                    val file = when (val d = record.detail) {
                        is Details.Task -> d.attachment
                        is Details.Expense -> d.receipt
                        is Details.Vendor -> d.attachment
                        is Details.Payment -> d.receipt
                        is Details.Document -> d.attachment
                        else -> ""
                    }
                    if (file.isNotBlank()) TextButton(onClick = { fileError = runCatching { LocalFiles.open(context, file) }.isFailure }) {
                        Text(s(R.string.open_attachment))
                    }
                    TextButton(onClick = { remove(record) }) { Text(s(R.string.delete)) }
                }
            }
        }
    }
    if (fileError) AlertDialog(onDismissRequest = { fileError = false }, title = { Text(s(R.string.file_problem)) },
        confirmButton = { TextButton(onClick = { fileError = false }) { Text(s(R.string.close)) } })
}

@Composable
private fun BudgetOverview(event: EventDetails, items: List<Item>, money: Money) {
    val totals = budget(event, items)
    Panel {
        Text(money.display(totals.remaining), style = MaterialTheme.typography.displaySmall)
        Text(s(R.string.remaining))
        if (event.budget > 0) LinearProgressIndicator(progress = { totals.spent.toDouble().div(event.budget).coerceIn(0.0, 1.0).toFloat() },
            modifier = Modifier.fillMaxWidth())
        listOf(R.string.budget to event.budget, R.string.planned to totals.planned, R.string.committed to totals.committed,
            R.string.paid_amount to totals.spent, R.string.vendor_balance to totals.vendorBalance).forEach { (label, amount) ->
            Text(s(R.string.choice_value, s(label), money.display(amount)))
        }
    }
    val none = s(R.string.uncategorised)
    val expenses = items.mapNotNull { it.detail as? Details.Expense }.filterNot { it.cancelled }
    if (expenses.isNotEmpty()) {
        Panel {
            Text(s(R.string.category_spending), style = MaterialTheme.typography.titleLarge)
            val categories = expenses.groupBy { it.category.ifBlank { none } }.toSortedMap()
            val max = categories.values.maxOf { group -> maxOf(group.sumOf { it.paid }, group.sumOf { it.planned }) }
            for ((category, group) in categories) {
                val paid = group.sumOf { it.paid }
                val planned = group.sumOf { it.planned }
                Text(s(R.string.choice_value, category, s(R.string.actual_vs_planned, money.display(paid), money.display(planned))),
                    color = if (paid > planned) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                if (max > 0) LinearProgressIndicator(progress = { paid.toDouble().div(max).toFloat() }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
