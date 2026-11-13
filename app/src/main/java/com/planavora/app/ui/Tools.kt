package com.planavora.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource as s
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.planavora.app.*
import com.planavora.app.R
import com.planavora.app.data.EventRow
import com.planavora.app.domain.*
import com.planavora.app.platform.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.Locale

private fun android.content.Context.app() = applicationContext as PlanavoraApplication

@Composable
fun LanguageChoice() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val current = remember(configuration) { AppCompatDelegate.getApplicationLocales().toLanguageTags() }
    val tags = listOf("", "en-GB", "en-US", "en-AU", "fr-FR", "fr-CA", "es-ES", "es-419", "de-DE", "it-IT",
        "pt-BR", "pt-PT", "nl-NL", "pl-PL", "sv-SE", "ja-JP", "ko", "zh-Hans", "zh-Hant", "hi-IN", "ar", "tr-TR", "id-ID")
    Choice(s(R.string.language), current, tags, {
        if (it.isBlank()) context.getString(R.string.device_default)
        else Locale.forLanguageTag(it).let { locale -> locale.getDisplayName(locale) }
    }) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it)) }
}

@Composable
fun NotificationToggle() {
    val context = LocalContext.current
    val store = context.app().settings
    val preferences by store.data.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    fun update(value: Boolean) {
        scope.launch { store.edit { it[remindersKey] = value } }
        if (!value) context.getSystemService(android.app.NotificationManager::class.java).cancel(1)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { update(it) }
    Toggle(R.string.reminders, preferences?.get(remindersKey) == true) { value ->
        if (value && Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else update(value)
    }
}

@Composable
fun SettingsPage(theme: String?, spoken: Boolean, onDelete: () -> Unit) {
    val context = LocalContext.current
    val store = context.app().settings
    val scope = rememberCoroutineScope()
    Page {
        Panel {
            LanguageChoice()
            val themes = mapOf("system" to R.string.device_default, "light" to R.string.light, "dark" to R.string.dark)
            Choice(s(R.string.theme), theme ?: "system", themes.keys.toList(), { context.getString(themes.getValue(it)) }) { next ->
                scope.launch { store.edit { it[darkKey] = next } }
            }
            NotificationToggle()
            Text(s(R.string.quiet_hours))
            Toggle(R.string.spoken, spoken) { value -> scope.launch { store.edit { it[spokenKey] = value } } }
        }
        Panel {
            Text(s(R.string.privacy), style = MaterialTheme.typography.titleLarge)
            Text(s(R.string.privacy_body))
            OutlinedButton(onClick = onDelete) { Text(s(R.string.delete_all)) }
        }
    }
}

@Composable
fun PremiumPage(billing: AmazonPremium, state: PremiumState) {
    LaunchedEffect(Unit) { billing.load() }
    Page {
        Hero(s(R.string.premium), s(R.string.premium_features))
        Panel {
            Text(s(if (state.active) R.string.premium_active else R.string.one_time))
            state.price?.let { Text(it, style = MaterialTheme.typography.headlineMedium) }
            val busy = state.status in listOf(StoreStatus.LOADING, StoreStatus.CHECKING)
            if (!state.active) Button(enabled = state.price != null && !busy, onClick = billing::purchase) { Text(s(R.string.buy)) }
            OutlinedButton(enabled = !busy, onClick = billing::restore) { Text(s(R.string.restore)) }
            val message = when (state.status) {
                StoreStatus.LOADING, StoreStatus.CHECKING -> R.string.loading
                StoreStatus.UNAVAILABLE -> R.string.store_unavailable
                StoreStatus.RESTORED -> if (state.active) R.string.premium_active else R.string.no_purchase
                StoreStatus.FAILED -> R.string.purchase_failed
                StoreStatus.CANCELLED -> R.string.cancelled
                StoreStatus.IDLE -> null
            }
            message?.let { Text(s(it)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportPage(event: EventRow, rows: List<Item>, premium: Boolean, paywall: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf<Section?>(null) }
    var sensitive by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Int?>(null) }
    var pending by remember { mutableStateOf<Report?>(null) }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val snapshot = pending
        pending = null
        if (uri != null && snapshot != null) scope.launch {
            status = withContext(Dispatchers.IO) { runCatching {
                requireNotNull(context.contentResolver.openOutputStream(uri)).use(snapshot::writePdf)
            }.fold({ R.string.export_saved }, { R.string.file_problem }) }
        }
    }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val snapshot = pending
        pending = null
        if (uri != null && snapshot != null) scope.launch {
            status = withContext(Dispatchers.IO) { runCatching {
                requireNotNull(context.contentResolver.openOutputStream(uri)).use { snapshot.writeCsv(it, context) }
            }.fold({ R.string.export_saved }, { R.string.file_problem }) }
        }
    }
    val report = remember(event, rows, section, sensitive, LocalConfiguration.current) {
        buildReport(context, event.title, event.details(), rows, section, sensitive)
    }
    Page {
        Panel {
            Choice(s(R.string.reports), section, listOf(null) + Section.entries, {
                context.getString(it?.label ?: R.string.event_summary)
            }) { section = it }
            Toggle(R.string.sensitive_export, sensitive) { if (it) confirm = true else sensitive = false }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { if (premium) { pending = report; pdf.launch("Planavora.pdf") } else paywall() }) { Text("PDF") }
                OutlinedButton(onClick = { if (premium) { pending = report; csv.launch("Planavora.csv") } else paywall() }) { Text("CSV") }
                OutlinedButton(onClick = {
                    if (report.text().length > 100_000) status = R.string.report_too_large
                    else context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, report.text()), context.getString(R.string.share)))
                }) { Text(s(R.string.share)) }
            }
            status?.let { Text(s(it)) }
        }
        Panel {
            Text(report.title, style = MaterialTheme.typography.headlineSmall)
            Text(report.heading)
            report.rows.take(30).forEach { (name, detail) -> Text(s(R.string.choice_value, name, detail)) }
            if (report.rows.size > 30) Text(s(R.string.report_preview_limit))
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(s(R.string.sensitive_confirm)) },
        confirmButton = { Button(onClick = { sensitive = true; confirm = false }) { Text(s(R.string.include)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(s(R.string.cancel)) } })
}

private data class Template(val name: Int, val offset: Long)
private fun templates(type: EventType): List<Template> {
    val signature = when (type) {
        EventType.BIRTHDAY -> R.string.template_cake
        EventType.WEDDING, EventType.ENGAGEMENT -> R.string.template_ceremony
        EventType.BABY_SHOWER -> R.string.template_games
        EventType.GRADUATION -> R.string.template_photos
        else -> R.string.template_schedule
    }
    return listOf(Template(R.string.template_venue, 45), Template(R.string.template_invitations, 30),
        Template(signature, 21), Template(R.string.template_catering, 10), Template(R.string.template_check, 2))
}

@Composable
fun AssistantPage(event: EventRow, rows: List<Item>, premium: Boolean, paywall: () -> Unit,
                  save: (List<Pair<String, Details.Task>>) -> Unit) {
    val context = LocalContext.current
    val eventDetails = remember(event) { event.details() }
    val existing = rows.filter { it.section == Section.TASK }.map { it.title.trim().lowercase() }.toSet()
    var draft by remember { mutableStateOf<List<Pair<String, Details.Task>>>(emptyList()) }
    var generated by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val totals = budget(eventDetails, rows)
    val outstanding = rows.filter { (it.detail as? Details.Task)?.complete == false }
    val date = eventDetails.date.takeIf(String::isNotBlank)?.let(LocalDate::parse)
    Page {
        Panel {
            Text(s(R.string.local_assistant), style = MaterialTheme.typography.headlineSmall)
            Text(s(R.string.local_assistant_help))
            Button(onClick = {
                if (!premium) paywall()
                else {
                    generated = true
                    draft = templates(eventDetails.type).map { template ->
                        context.getString(template.name) to Details.Task(due = date?.minusDays(template.offset)?.toString().orEmpty())
                    }.filter { it.first.lowercase() !in existing }
                }
            }) { Text(s(R.string.draft_checklist)) }
            if (generated && draft.isEmpty()) Text(s(R.string.duplicate_tasks))
        }
        if (draft.isNotEmpty()) Panel {
            Text(s(R.string.review_draft), style = MaterialTheme.typography.titleLarge)
            draft.forEachIndexed { index, (title, task) ->
                Field(title, R.string.name) { changed -> draft = draft.mapIndexed { i, row -> if (i == index) changed to task else row } }
                DateField(task.due, R.string.due_date) { due -> draft = draft.mapIndexed { i, row -> if (i == index) title to task.copy(due = due) else row } }
                TextButton(onClick = { draft = draft.filterIndexed { i, _ -> index != i } }) { Text(s(R.string.remove)) }
            }
            Button(enabled = draft.all { it.first.isNotBlank() }, onClick = {
                save(draft.map { it.first.trim() to it.second })
                draft = emptyList()
                generated = false
            }) { Text(s(R.string.save_draft)) }
        }
        Panel {
            Text(s(R.string.outstanding_tasks), style = MaterialTheme.typography.titleLarge)
            if (outstanding.isEmpty()) Text(s(R.string.no_records))
            outstanding.take(8).forEach { item -> Text(item.title) }
            val money = Money(locale, eventDetails.currency)
            Text(s(R.string.choice_value, s(R.string.remaining), money.display(totals.remaining)))
            Text(s(R.string.choice_value, s(R.string.vendor_balance), money.display(totals.vendorBalance)))
        }
        Panel {
            Text(s(R.string.invitation_draft), style = MaterialTheme.typography.titleLarge)
            Text(s(R.string.invitation_line, event.title))
            if (date != null) Text(localDate(date.toString(), locale))
            if (eventDetails.location.isNotBlank()) Text(eventDetails.location)
            if (eventDetails.rsvpDeadline.isNotBlank()) Text(s(R.string.choice_value, s(R.string.rsvp_deadline), localDate(eventDetails.rsvpDeadline, locale)))
        }
    }
}
