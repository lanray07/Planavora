package com.planavora.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource as s
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.planavora.app.PlanModel
import com.planavora.app.R
import com.planavora.app.data.*
import com.planavora.app.domain.*
import com.planavora.app.platform.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun localDate(date: String, locale: Locale) = if (date.isBlank()) "" else
    LocalDate.parse(date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
fun localTime(time: String, locale: Locale) = if (time.isBlank()) "" else
    LocalTime.parse(time).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

private enum class Tab(val label: Int) {
    DASHBOARD(R.string.dashboard), PLANNING(R.string.planning), GUESTS(R.string.guests), BUDGET(R.string.budget), MORE(R.string.more)
}
private enum class Tool(val label: Int) {
    EVENTS(R.string.events), VOICE(R.string.voice), ASSISTANT(R.string.assistant), REPORT(R.string.reports),
    SETTINGS(R.string.settings), PREMIUM(R.string.premium)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Planavora(model: PlanModel) {
    val prefs by model.settings.data.collectAsStateWithLifecycle(initialValue = null)
    PlanTheme(prefs?.get(darkKey)) {
        val events by model.events.collectAsStateWithLifecycle()
        val current by model.event.collectAsStateWithLifecycle()
        val watched by model.items.collectAsStateWithLifecycle()
        val rows = if (watched.first == current?.id) watched.second else emptyList()
        val premium by model.billing.state.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val locale = LocalConfiguration.current.locales[0]
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        var tab by rememberSaveable { mutableStateOf(Tab.DASHBOARD) }
        var section by rememberSaveable { mutableStateOf<Section?>(null) }
        var tool by rememberSaveable { mutableStateOf<Tool?>(null) }
        var editingEvent by rememberSaveable { mutableStateOf(false) }
        var editingItem by remember { mutableStateOf<Section?>(null) }
        var item by remember { mutableStateOf<Item?>(null) }
        var draft by remember { mutableStateOf<Pair<String, Details>?>(null) }
        var history by remember { mutableStateOf<Item?>(null) }
        var confirmation by remember { mutableStateOf<Pair<Int, () -> Unit>?>(null) }
        var saving by remember { mutableStateOf(false) }
        val details = remember(current) { current?.details() }
        val money = details?.let { Money(locale, it.currency) }
        val tablet = LocalConfiguration.current.screenWidthDp >= 700

        fun select(next: Tab) { tab = next; section = null; tool = null }
        fun show(next: Section) { section = next; tool = null }
        fun open(next: Tool) { tool = next; section = null }
        fun edit(next: Section, existing: Item? = null, initial: Pair<String, Details>? = null) {
            editingItem = next; item = existing; draft = initial
        }
        fun feedback(result: SaveResult) {
            if (result == SaveResult.SAVED) return
            val message = when (result) {
                SaveResult.PREMIUM -> R.string.premium_required
                SaveResult.CAPACITY -> R.string.capacity_error
                SaveResult.CURRENCY -> R.string.currency_error
                SaveResult.LINKED -> R.string.linked_error
                SaveResult.DUPLICATE -> R.string.duplicate_tasks
                else -> R.string.record_error
            }
            scope.launch { snackbar.showSnackbar(context.getString(message)) }
        }
        fun runSave(operation: suspend () -> Unit) {
            if (saving) return
            saving = true
            scope.launch {
                try { operation() }
                catch (exception: kotlinx.coroutines.CancellationException) { throw exception }
                catch (_: Exception) { feedback(SaveResult.MISSING) }
                finally { saving = false }
            }
        }
        BackHandler(tool != null || section != null || tab != Tab.DASHBOARD) {
            if (tool != null) tool = null
            else if (section != null) section = null
            else select(Tab.DASHBOARD)
        }
        val icons = listOf(Icons.Default.Home, Icons.Default.List, Icons.Default.Person, Icons.Default.Star, Icons.Default.MoreVert)
        val activeSection = section ?: when (tab) {
            Tab.PLANNING -> Section.TASK
            Tab.GUESTS -> Section.GUEST
            Tab.BUDGET -> Section.EXPENSE
            else -> null
        }
        val screen = tool?.label ?: section?.label ?: tab.label
        Scaffold(snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text(s(if (screen == R.string.dashboard) R.string.app_name else screen), style = MaterialTheme.typography.headlineSmall) },
                    navigationIcon = {
                        if (tool != null || section != null) TextButton(onClick = { tool = null; section = null }) { Text(s(R.string.back)) }
                    },
                    actions = {
                        if (tool != Tool.EVENTS) TextButton(onClick = { open(Tool.EVENTS) }) { Text(s(R.string.events)) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            bottomBar = {
                if (!tablet) NavigationBar {
                    Tab.entries.forEachIndexed { index, entry ->
                        NavigationBarItem(selected = tab == entry, onClick = { select(entry) },
                            icon = { Icon(icons[index], null) }, label = { Text(s(entry.label), maxLines = 1, softWrap = false) })
                    }
                }
            }
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (tablet) NavigationRail {
                    Tab.entries.forEachIndexed { index, entry ->
                        NavigationRailItem(selected = tab == entry, onClick = { select(entry) },
                            icon = { Icon(icons[index], null) }, label = { Text(s(entry.label)) }, alwaysShowLabel = true)
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                    key(tab, section, tool, current?.id) {
                        when {
                            tool == Tool.EVENTS -> Page {
                                Button(onClick = { editingEvent = true }) { Text(s(R.string.new_event)) }
                                events.orEmpty().forEach { event ->
                                    Panel {
                                        Text(event.title, style = MaterialTheme.typography.titleLarge)
                                        Text(listOf(localDate(event.details().date, locale), if (event.archived) s(R.string.archived) else "")
                                            .filter(String::isNotBlank).joinToString(" · "))
                                        OutlinedButton(onClick = { model.selected.value = event.id; select(Tab.DASHBOARD) }) { Text(s(R.string.open)) }
                                    }
                                }
                            }
                            tool == Tool.SETTINGS -> SettingsPage(prefs?.get(darkKey), prefs?.get(spokenKey) == true, onDelete = {
                                confirmation = R.string.confirm_delete_all to {
                                    runSave {
                                        model.store.dao.deleteAll()
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            java.io.File(context.filesDir, "attachments").listFiles()?.forEach { it.delete() }
                                        }
                                        model.selected.value = null
                                        select(Tab.DASHBOARD)
                                    }
                                }
                            })
                            tool == Tool.PREMIUM -> PremiumPage(model.billing, premium)
                            events == null -> CircularProgressIndicator(Modifier.padding(24.dp))
                            current == null || details == null || money == null -> Page {
                                Hero(s(R.string.tagline), s(R.string.first_event_help))
                                Button(onClick = { editingEvent = true }) { Text(s(R.string.new_event)) }
                            }
                            else -> {
                                val event = current!!
                                when {
                                    tool == Tool.VOICE -> VoicePage(event, details, rows, money, prefs?.get(spokenKey) == true,
                                        navigate = ::show, draftTask = { edit(Section.TASK, initial = it to Details.Task()) },
                                        draftExpense = { name, amount -> edit(Section.EXPENSE, initial = name to Details.Expense(planned = amount)) })
                                    tool == Tool.ASSISTANT -> AssistantPage(event, rows, premium.active, { open(Tool.PREMIUM) }) { tasks ->
                                        runSave { feedback(model.store.addTasks(event.id, tasks)) }
                                    }
                                    tool == Tool.REPORT -> ReportPage(event, rows, premium.active) { open(Tool.PREMIUM) }
                                    activeSection != null -> Records(activeSection, rows, details, locale, money,
                                        add = { edit(activeSection) }, edit = { edit(activeSection, it) }, show = ::show,
                                        history = { history = it },
                                        remove = { record ->
                                            confirmation = R.string.confirm_delete to { runSave { feedback(model.store.deleteItem(event.id, record.id)) } }
                                        },
                                        change = { record, value ->
                                            runSave { feedback(model.store.saveItem(event.id, record.id, record.section, record.title, value, premium.active)) }
                                        })
                                    tab == Tab.MORE -> Page {
                                        listOf(Section.VENDOR, Section.PAYMENT, Section.SEATING, Section.DOCUMENT).forEach { entry ->
                                            Option(s(entry.label)) { show(entry) }
                                        }
                                        listOf(Tool.REPORT, Tool.VOICE, Tool.ASSISTANT, Tool.SETTINGS, Tool.PREMIUM).forEach { entry ->
                                            Option(s(entry.label)) { open(entry) }
                                        }
                                    }
                                    else -> Dashboard(event, details, rows, money, locale, tablet, ::show,
                                        edit = { editingEvent = true }, archive = {
                                            runSave { feedback(model.store.setArchived(event.id, !event.archived, premium.active)) }
                                        },
                                        duplicate = {
                                            runSave {
                                                val (result, id) = model.store.duplicate(event.id, context.getString(R.string.copy_name, event.title), premium.active)
                                                if (id != null) model.selected.value = id
                                                feedback(result)
                                            }
                                        },
                                        remove = {
                                            confirmation = R.string.confirm_delete to {
                                                runSave {
                                                    model.store.dao.deleteEvent(event.id)
                                                    model.selected.value = null
                                                }
                                            }
                                        })
                                }
                            }
                        }
                    }
                }
            }
        }
        if (editingEvent) {
            val original = current.takeUnless { tool == Tool.EVENTS }
            EventEditor(original, locale, { editingEvent = false }) { title, updated ->
                runSave {
                    val (result, id) = model.store.saveEvent(original?.id, title, updated, premium.active)
                    if (result == SaveResult.SAVED) {
                        editingEvent = false
                        model.selected.value = id
                        select(Tab.DASHBOARD)
                    } else if (result == SaveResult.PREMIUM) {
                        editingEvent = false
                        open(Tool.PREMIUM)
                    }
                    feedback(result)
                }
            }
        }
        editingItem?.let { selected ->
            val event = current
            if (event != null && money != null) ItemEditor(selected, item, rows, money, { editingItem = null }, draft) { title, updated ->
                runSave {
                    val result = model.store.saveItem(event.id, item?.id, selected, title, updated, premium.active)
                    if (result == SaveResult.SAVED) editingItem = null
                    if (result == SaveResult.PREMIUM) { editingItem = null; open(Tool.PREMIUM) }
                    feedback(result)
                }
            }
        }
        history?.let { record -> HistoryDialog(model, record, locale) { history = null } }
        confirmation?.let { (label, action) ->
            AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(s(label)) },
                confirmButton = { Button(onClick = { confirmation = null; action() }) { Text(s(R.string.delete)) } },
                dismissButton = { TextButton(onClick = { confirmation = null }) { Text(s(R.string.cancel)) } })
        }
    }
}

@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.widthIn(max = 1150.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}

@Composable
private fun Option(title: String, open: () -> Unit) {
    OutlinedCard(onClick = open, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
        Text(title, Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun HistoryDialog(model: PlanModel, record: Item, locale: Locale, close: () -> Unit) {
    val context = LocalContext.current
    val history by remember(record.id) { model.store.dao.observeHistory(record.id) }.collectAsStateWithLifecycle(emptyList())
    fun value(raw: String): String = when (raw) {
        "" -> ""
        "true" -> context.getString(if (record.section == Section.PAYMENT) R.string.paid else R.string.complete)
        "false" -> context.getString(R.string.pending)
        else -> context.getString(Rsvp.valueOf(raw).label)
    }
    AlertDialog(onDismissRequest = close, title = { Text(s(R.string.history)) }, text = {
        Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (history.isEmpty()) Text(s(R.string.no_records))
            history.forEach { change ->
                val date = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale)
                    .withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(change.instant))
                Text(listOf(date, value(change.fromValue), value(change.toValue)).filter(String::isNotBlank).joinToString(" · "))
            }
        }
    }, confirmButton = { TextButton(onClick = close) { Text(s(R.string.close)) } })
}
