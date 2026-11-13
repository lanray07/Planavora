package com.planavora.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource as s
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.planavora.app.R
import com.planavora.app.data.EventRow
import com.planavora.app.domain.*
import com.planavora.app.platform.LocalFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun Hero(title: String, subtitle: String, cover: String = "", eyebrow: String = "") {
    val context = LocalContext.current
    val photo by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, cover) {
        value = if (cover.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val path = LocalFiles.file(context, cover).path
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                val sample = generateSequence(1) { it * 2 }.first { maxOf(bounds.outWidth, bounds.outHeight) / it <= 1600 }
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(Modifier.fillMaxWidth().heightIn(min = 200.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xff493a58))) {
        val bitmap = photo
        if (bitmap != null) Image(bitmap, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        else Image(painterResource(R.drawable.invitation), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Color(0xff251721).copy(alpha = 0.64f)))
        Column(Modifier.align(Alignment.CenterStart).padding(24.dp).widthIn(max = 660.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            if (eyebrow.isNotBlank()) Text(eyebrow, color = Color(0xfff1d49f), style = MaterialTheme.typography.labelLarge)
            Text(title, color = Color.White, style = MaterialTheme.typography.displaySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Color(0xfffff6ee), style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Dashboard(event: EventRow, details: EventDetails, items: List<Item>, money: Money, locale: Locale, wide: Boolean,
              navigate: (Section) -> Unit, edit: () -> Unit, archive: () -> Unit, duplicate: () -> Unit, remove: () -> Unit) {
    val today = LocalDate.now(runCatching { ZoneId.of(details.zone) }.getOrDefault(ZoneId.systemDefault()))
    val days = details.date.takeIf(String::isNotBlank)?.let { ChronoUnit.DAYS.between(today, LocalDate.parse(it)).toInt() }
    val countdown = when {
        days == null -> s(R.string.no_event_date)
        days == 0 -> s(R.string.today)
        days > 0 -> pluralStringResource(R.plurals.days_left, days, NumberFormat.getIntegerInstance(locale).format(days))
        else -> s(R.string.event_passed)
    }
    val totals = budget(details, items)
    val guests = items.mapNotNull { it.detail as? Details.Guest }
    val activeTasks = items.count { (it.detail as? Details.Task)?.complete == false }
    val confirmed = guests.filter { it.rsvp == Rsvp.CONFIRMED }.sumOf { it.headcount }
    val waiting = guests.count { it.invited && it.rsvp in listOf(Rsvp.PENDING, Rsvp.MAYBE) }
    val numbers = NumberFormat.getIntegerInstance(locale)
    val deadlines = items.mapNotNull { i -> dueDate(i.detail)?.let { i to it } }.sortedBy { it.second }
    val readiness = readiness(details, items)
    Page {
        Hero(event.title, listOf(countdown, localDate(details.date, locale), localTime(details.time, locale), details.location)
            .filter(String::isNotBlank).joinToString(" · "), details.cover, s(R.string.command_centre))
        val metrics = listOf(
            Triple(R.string.remaining, money.display(totals.remaining), Section.EXPENSE),
            Triple(R.string.confirmed_guests, numbers.format(confirmed), Section.GUEST),
            Triple(R.string.outstanding_tasks, numbers.format(activeTasks), Section.TASK),
            Triple(R.string.vendor_balance, money.display(totals.vendorBalance), Section.PAYMENT)
        ).filter { details.guestTracking || it.third != Section.GUEST }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = if (wide) 4 else 2) {
            metrics.forEachIndexed { index, (label, amount, section) ->
                Card(onClick = { navigate(section) }, modifier = Modifier.weight(1f).widthIn(min = 124.dp).heightIn(min = 102.dp),
                    colors = CardDefaults.cardColors(containerColor = when (index) {
                        1 -> MaterialTheme.colorScheme.secondaryContainer
                        3 -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    })) {
                    Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(s(label), style = MaterialTheme.typography.labelMedium)
                        Text(amount, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
        val readinessPanel: @Composable () -> Unit = {
            Panel {
                Text(s(R.string.readiness), style = MaterialTheme.typography.titleLarge)
                readiness.forEach { (kind, value) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s(kind.label), Modifier.weight(1f))
                        Text(if (value.total == 0) s(R.string.no_records) else s(R.string.readiness_count, numbers.format(value.done), numbers.format(value.total)))
                    }
                    if (value.total > 0) LinearProgressIndicator(progress = { value.done.toFloat() / value.total }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        val deadlinePanel: @Composable () -> Unit = {
            Panel {
                Text(s(R.string.deadlines), style = MaterialTheme.typography.titleLarge)
                if (details.rsvpDeadline.isNotBlank() && waiting > 0) {
                    Text(s(R.string.choice_value, s(R.string.rsvp_deadline), localDate(details.rsvpDeadline, locale)))
                }
                if (deadlines.isEmpty()) Text(s(R.string.no_deadlines))
                deadlines.take(6).forEach { (entry, date) ->
                    OutlinedButton(onClick = { navigate(entry.section) }) {
                        val formattedDate = localDate(date.toString(), locale)
                        if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
                            Column {
                                Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(formattedDate)
                            }
                        } else {
                            Text(s(R.string.choice_value, entry.title, formattedDate), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        if (wide) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) { readinessPanel() }
            Box(Modifier.weight(1f)) { deadlinePanel() }
        } else { readinessPanel(); deadlinePanel() }
        Panel {
            Text(s(R.string.event_summary), style = MaterialTheme.typography.titleLarge)
            Text(s(R.string.choice_value, s(R.string.budget), money.display(details.budget)))
            Text(s(R.string.choice_value, s(R.string.planned), money.display(totals.planned)))
            Text(s(R.string.choice_value, s(R.string.paid_amount), money.display(totals.spent)))
            if (details.guestTracking) Text(s(R.string.choice_value, s(R.string.pending_rsvps), numbers.format(waiting)))
            if (details.capacity > 0 && confirmed > details.capacity) Text(s(R.string.capacity_warning), color = MaterialTheme.colorScheme.error)
            if (totals.remaining < 0) Text(s(R.string.over_budget), color = MaterialTheme.colorScheme.error)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = edit) { Text(s(R.string.edit_event)) }
            TextButton(onClick = archive) { Text(s(if (event.archived) R.string.unarchive else R.string.archive)) }
            TextButton(onClick = duplicate) { Text(s(R.string.duplicate)) }
            TextButton(onClick = remove) { Text(s(R.string.delete)) }
        }
    }
}
