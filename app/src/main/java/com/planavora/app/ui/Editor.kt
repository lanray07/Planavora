package com.planavora.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource as s
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.planavora.app.R
import com.planavora.app.data.EventRow
import com.planavora.app.domain.*
import com.planavora.app.platform.LocalFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Currency
import java.util.Locale

val Section.label get() = when (this) {
    Section.TASK -> R.string.tasks
    Section.GUEST -> R.string.guests
    Section.EXPENSE -> R.string.expenses
    Section.VENDOR -> R.string.vendors
    Section.PAYMENT -> R.string.payments
    Section.TIMELINE -> R.string.timeline
    Section.SEATING -> R.string.seating
    Section.DOCUMENT -> R.string.documents
}
val Rsvp.label get() = when (this) {
    Rsvp.PENDING -> R.string.pending
    Rsvp.CONFIRMED -> R.string.confirmed
    Rsvp.MAYBE -> R.string.maybe
    Rsvp.DECLINED -> R.string.declined
}
val EventType.label get() = when (this) {
    EventType.BIRTHDAY -> R.string.birthday
    EventType.WEDDING -> R.string.wedding
    EventType.BABY_SHOWER -> R.string.baby_shower
    EventType.ANNIVERSARY -> R.string.anniversary
    EventType.GRADUATION -> R.string.graduation
    EventType.ENGAGEMENT -> R.string.engagement
    EventType.REUNION -> R.string.reunion
    EventType.COMMUNITY -> R.string.community
    EventType.CUSTOM -> R.string.custom
}
val Readiness.label get() = when (this) {
    Readiness.TASKS -> R.string.tasks
    Readiness.GUESTS -> R.string.guests
    Readiness.VENDORS -> R.string.vendors
    Readiness.BUDGET -> R.string.budget
    Readiness.TIMELINE -> R.string.timeline
}

@Composable
fun <T> Choice(label: String, current: T, options: List<T>, display: (T) -> String, change: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(s(R.string.choice_value, label, display(current)))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(display(option)) }, onClick = { change(option); expanded = false })
            }
        }
    }
}

@Composable
fun Field(value: String, label: Int, modifier: Modifier = Modifier, keyboard: KeyboardType = KeyboardType.Text,
          single: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.take(4000)) }, modifier.fillMaxWidth(),
        label = { Text(s(label)) }, singleLine = single, keyboardOptions = KeyboardOptions(keyboardType = keyboard))
}

@Composable
fun Toggle(label: Int, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(s(label), Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = change)
    }
}

@Composable
fun DateField(raw: String, label: Int, update: (String) -> Unit) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = {
            val date = raw.takeIf(String::isNotBlank)?.let(LocalDate::parse) ?: LocalDate.now()
            DatePickerDialog(context, { _, year, month, day -> update(LocalDate.of(year, month + 1, day).toString()) },
                date.year, date.monthValue - 1, date.dayOfMonth).show()
        }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
            Text(if (raw.isBlank()) s(label) else s(R.string.choice_value, s(label), localDate(raw, context.resources.configuration.locales[0])))
        }
        if (raw.isNotBlank()) TextButton(onClick = { update("") }) { Text(s(R.string.clear)) }
    }
}

@Composable
fun TimeField(raw: String, label: Int, update: (String) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val time = raw.takeIf(String::isNotBlank)?.let(LocalTime::parse) ?: LocalTime.of(12, 0)
        TimePickerDialog(context, { _, hour, minute -> update(LocalTime.of(hour, minute).toString()) },
            time.hour, time.minute, android.text.format.DateFormat.is24HourFormat(context)).show()
    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(if (raw.isBlank()) s(label) else s(R.string.choice_value, s(label), localTime(raw, context.resources.configuration.locales[0])))
    }
}

@Composable
fun Attachment(path: String, photos: Boolean = false, change: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) { runCatching { LocalFiles.import(context, uri, photos) } }
            result.onSuccess(change)
            error = result.isFailure
            busy = false
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = !busy, onClick = { picker.launch(if (photos) arrayOf("image/*") else arrayOf("image/*", "application/pdf", "text/plain")) }) {
            Text(s(if (busy) R.string.loading else if (path.isBlank()) R.string.attach else R.string.replace_attachment))
        }
        if (path.isNotBlank()) TextButton(onClick = { change("") }) { Text(s(R.string.clear)) }
    }
    if (error) Text(s(R.string.file_problem), color = MaterialTheme.colorScheme.error)
}

@Composable
fun EventEditor(event: EventRow?, locale: Locale, close: () -> Unit, save: (String, EventDetails) -> Unit) {
    val original = remember(event?.id) {
        event?.details() ?: EventDetails(currency = runCatching { Currency.getInstance(locale).currencyCode }.getOrDefault("GBP"),
            zone = ZoneId.systemDefault().id)
    }
    var details by remember { mutableStateOf(original) }
    var title by rememberSaveable { mutableStateOf(event?.title.orEmpty()) }
    var moneyInput by rememberSaveable { mutableStateOf(Money(locale, original.currency).input(original.budget)) }
    var capacity by rememberSaveable { mutableStateOf(original.capacity.takeIf { it > 0 }?.toString().orEmpty()) }
    var error by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val currencyOptions = remember { Currency.getAvailableCurrencies().filter { it.defaultFractionDigits >= 0 }.map { it.currencyCode }.sorted() }
    EditDialog(if (event == null) R.string.new_event else R.string.edit_event, error, close, {
        val budget = Money(locale, details.currency).parseOptional(moneyInput)
        val count = count(capacity)
        val validTime = details.time.isBlank() || details.date.isNotBlank() &&
            ZoneId.of(details.zone).rules.getValidOffsets(LocalDateTime.of(LocalDate.parse(details.date), LocalTime.parse(details.time))).size == 1
        if (title.isBlank() || budget == null || count == null || !validTime) error = true
        else save(title.trim(), details.copy(budget = budget, capacity = count))
    }) {
        if (event == null) LanguageChoice()
        Field(title, R.string.event_name) { title = it }
        Choice(s(R.string.event_type), details.type, EventType.entries, { context.getString(it.label) }) { details = details.copy(type = it) }
        DateField(details.date, R.string.event_date) { details = details.copy(date = it, time = if (it.isBlank()) "" else details.time) }
        if (details.date.isNotBlank()) TimeField(details.time, R.string.event_time) { details = details.copy(time = it) }
        Field(details.location, R.string.location) { details = details.copy(location = it) }
        Field(details.venue, R.string.venue) { details = details.copy(venue = it) }
        Choice(s(R.string.currency), details.currency, currencyOptions, { it }) { details = details.copy(currency = it) }
        Field(moneyInput, R.string.budget, keyboard = KeyboardType.Decimal) { moneyInput = it }
        Field(capacity, R.string.capacity, keyboard = KeyboardType.Number) { capacity = it }
        Toggle(R.string.guest_tracking, details.guestTracking) { details = details.copy(guestTracking = it) }
        if (details.guestTracking) DateField(details.rsvpDeadline, R.string.rsvp_deadline) { details = details.copy(rsvpDeadline = it) }
        if (event == null) NotificationToggle()
        Field(details.notes, R.string.notes, single = false) { details = details.copy(notes = it) }
        Attachment(details.cover, photos = true) { details = details.copy(cover = it) }
        Text(s(R.string.readiness), style = MaterialTheme.typography.titleMedium)
        Readiness.entries.forEach { category ->
            Toggle(category.label, category in details.readiness) { enabled ->
                details = details.copy(readiness = if (enabled) details.readiness + category else details.readiness - category)
            }
        }
    }
}

private fun count(value: String): Int? = if (value.isBlank()) 0 else value.toIntOrNull()?.takeIf { it in 0..100000 }

fun defaults(section: Section): Details = when (section) {
    Section.TASK -> Details.Task()
    Section.GUEST -> Details.Guest()
    Section.EXPENSE -> Details.Expense()
    Section.VENDOR -> Details.Vendor()
    Section.PAYMENT -> Details.Payment()
    Section.TIMELINE -> Details.Timeline()
    Section.SEATING -> Details.Seating()
    Section.DOCUMENT -> Details.Document()
}

@Composable
fun ItemEditor(section: Section, item: Item?, rows: List<Item>, money: Money, close: () -> Unit,
               initial: Pair<String, Details>? = null, save: (String, Details) -> Unit) {
    var title by rememberSaveable { mutableStateOf(item?.title ?: initial?.first.orEmpty()) }
    var details by remember { mutableStateOf(item?.detail ?: initial?.second ?: defaults(section)) }
    val amounts = remember { mutableStateMapOf<Int, String>() }
    var error by remember { mutableStateOf(false) }
    val context = LocalContext.current
    fun numeric(label: Int, original: Int) = count(amounts[label] ?: original.toString())
    fun amount(label: Int, original: Long) = money.parseOptional(amounts[label] ?: money.input(original))

    @Composable fun Amount(label: Int, original: Long) {
        Field(amounts[label] ?: money.input(original), label, keyboard = KeyboardType.Decimal) { amounts[label] = it }
    }
    @Composable fun Number(label: Int, original: Int) {
        Field(amounts[label] ?: original.toString(), label, keyboard = KeyboardType.Number) { amounts[label] = it }
    }
    @Composable fun Link(label: Int, target: Section, current: String, update: (String) -> Unit, optional: Boolean = true) {
        val links = rows.filter { it.section == target }.associate { it.id to it.title }
        Choice(s(label), current, (if (optional) listOf("") else emptyList()) + links.keys, { links[it] ?: context.getString(R.string.none) }, update)
    }

    EditDialog(section.label, error, close, {
        val output = when (val d = details) {
            is Details.Task -> d
            is Details.Guest -> {
                val plus = numeric(R.string.plus_ones, d.plusOnes)
                val head = numeric(R.string.headcount, d.headcount)
                if (plus == null || head == null || head !in 1..(plus + 1)) null else d.copy(plusOnes = plus, headcount = head)
            }
            is Details.Expense -> {
                val planned = amount(R.string.planned, d.planned)
                val committed = amount(R.string.committed, d.committed)
                val paid = amount(R.string.paid_amount, d.paid)
                val deposit = amount(R.string.deposit, d.deposit)
                if (planned == null || committed == null || paid == null || deposit == null || deposit > paid) null
                else d.copy(planned = planned, committed = committed, paid = paid, deposit = deposit)
            }
            is Details.Vendor -> {
                val quote = amount(R.string.quote, d.quote)
                val agreed = amount(R.string.agreed, d.agreed)
                if (quote == null || agreed == null) null else d.copy(quote = quote, agreed = agreed)
            }
            is Details.Payment -> {
                val value = amount(R.string.amount, d.amount)
                if (value == null || value == 0L || d.vendorId.isBlank()) null else d.copy(amount = value)
            }
            is Details.Timeline -> d.takeIf { it.start.isNotBlank() && it.end.isNotBlank() && LocalTime.parse(it.start) < LocalTime.parse(it.end) }
            is Details.Seating -> numeric(R.string.capacity, d.capacity)?.let { d.copy(capacity = it) }
            is Details.Document -> d.takeIf { it.attachment.isNotBlank() }
        }
        if (title.isBlank() || output == null) error = true else save(title.trim(), output)
    }) {
        Field(title, R.string.name) { title = it }
        when (val d = details) {
            is Details.Task -> {
                Field(d.category, R.string.category) { details = d.copy(category = it) }
                DateField(d.due, R.string.due_date) { details = d.copy(due = it) }
                val priorities = listOf(R.string.low, R.string.normal, R.string.high)
                Choice(s(R.string.priority), d.priority, listOf(0, 1, 2), { context.getString(priorities[it]) }) { details = d.copy(priority = it) }
                Toggle(R.string.complete, d.complete) { details = d.copy(complete = it) }
                Attachment(d.attachment) { details = d.copy(attachment = it) }
            }
            is Details.Guest -> {
                Field(d.household, R.string.household) { details = d.copy(household = it) }
                Field(d.contact, R.string.contact) { details = d.copy(contact = it) }
                Toggle(R.string.invited, d.invited) { details = d.copy(invited = it) }
                Choice(s(R.string.rsvp), d.rsvp, Rsvp.entries, { context.getString(it.label) }) { details = d.copy(rsvp = it) }
                Number(R.string.plus_ones, d.plusOnes)
                Number(R.string.headcount, d.headcount)
                Field(d.dietary, R.string.dietary) { details = d.copy(dietary = it) }
                Link(R.string.seating, Section.SEATING, d.groupId, { details = d.copy(groupId = it) })
            }
            is Details.Expense -> {
                Field(d.category, R.string.category) { details = d.copy(category = it) }
                Link(R.string.vendors, Section.VENDOR, d.vendorId, { details = d.copy(vendorId = it) })
                Amount(R.string.planned, d.planned)
                Amount(R.string.committed, d.committed)
                Amount(R.string.paid_amount, d.paid)
                Amount(R.string.deposit, d.deposit)
                Text(s(R.string.deposit_help), style = MaterialTheme.typography.bodySmall)
                DateField(d.due, R.string.due_date) { details = d.copy(due = it) }
                Toggle(R.string.cancelled, d.cancelled) { details = d.copy(cancelled = it) }
                Attachment(d.receipt) { details = d.copy(receipt = it) }
            }
            is Details.Vendor -> {
                Field(d.business, R.string.business) { details = d.copy(business = it) }
                Field(d.category, R.string.category) { details = d.copy(category = it) }
                Field(d.phone, R.string.phone, keyboard = KeyboardType.Phone) { details = d.copy(phone = it) }
                Field(d.email, R.string.email, keyboard = KeyboardType.Email) { details = d.copy(email = it) }
                Field(d.website, R.string.website, keyboard = KeyboardType.Uri) { details = d.copy(website = it) }
                Amount(R.string.quote, d.quote)
                Amount(R.string.agreed, d.agreed)
                Toggle(R.string.booked, d.booked) { details = d.copy(booked = it) }
                Attachment(d.attachment) { details = d.copy(attachment = it) }
            }
            is Details.Payment -> {
                Link(R.string.vendors, Section.VENDOR, d.vendorId, { details = d.copy(vendorId = it) }, optional = false)
                Amount(R.string.amount, d.amount)
                DateField(d.due, R.string.due_date) { details = d.copy(due = it) }
                Toggle(R.string.paid, d.paid) { details = d.copy(paid = it) }
                Toggle(R.string.deposit, d.deposit) { details = d.copy(deposit = it) }
                Field(d.method, R.string.method) { details = d.copy(method = it) }
                Attachment(d.receipt) { details = d.copy(receipt = it) }
            }
            is Details.Timeline -> {
                TimeField(d.start, R.string.start_time) { details = d.copy(start = it) }
                TimeField(d.end, R.string.end_time) { details = d.copy(end = it) }
                Field(d.person, R.string.person) { details = d.copy(person = it) }
                Field(d.location, R.string.location) { details = d.copy(location = it) }
                Link(R.string.vendors, Section.VENDOR, d.vendorId, { details = d.copy(vendorId = it) })
                Toggle(R.string.complete, d.complete) { details = d.copy(complete = it) }
            }
            is Details.Seating -> Number(R.string.capacity, d.capacity)
            is Details.Document -> Attachment(d.attachment) { details = d.copy(attachment = it) }
        }
        Field(details.notes, R.string.notes, single = false) { note ->
            details = when (val d = details) {
                is Details.Task -> d.copy(notes = note)
                is Details.Guest -> d.copy(notes = note)
                is Details.Expense -> d.copy(notes = note)
                is Details.Vendor -> d.copy(notes = note)
                is Details.Payment -> d.copy(notes = note)
                is Details.Timeline -> d.copy(notes = note)
                is Details.Seating -> d.copy(notes = note)
                is Details.Document -> d.copy(notes = note)
            }
        }
    }
}

@Composable
private fun EditDialog(label: Int, error: Boolean, close: () -> Unit, save: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest = close, title = { Text(s(label)) }, text = {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (error) Text(s(R.string.invalid), color = MaterialTheme.colorScheme.error)
            content()
        }
    }, confirmButton = { Button(onClick = save) { Text(s(R.string.save)) } },
        dismissButton = { TextButton(onClick = close) { Text(s(R.string.cancel)) } })
}
