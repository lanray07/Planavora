package com.planavora.app

import android.content.Context
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.*
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.planavora.app.data.*
import com.planavora.app.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class FireJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = ApplicationProvider.getApplicationContext<Context>() as PlanavoraApplication

    private fun tap(text: String) = compose.onAllNodesWithText(text).onFirst().performClick()
    private fun waitFor(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun createEventTaskThenRestart() {
        instrumentation.runOnMainSync { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en-GB")) }
        runBlocking { app.store.dao.deleteAll() }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("Create event")
            tap("Create event")
            compose.onNodeWithText("Event name").performTextInput("Lantern supper")
            tap("Save")
            waitFor("Lantern supper")
            tap("Planning")
            tap("Add record")
            compose.onNodeWithText("Name").performTextInput("Reserve the garden")
            tap("Save")
            waitFor("Reserve the garden")
            compose.onAllNodesWithText("Complete").onLast().performClick()
            waitFor("Undo")
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("Lantern supper")
            tap("Planning")
            waitFor("Reserve the garden")
            compose.onNodeWithText("Undo").assertExists()
        }
    }

    @Test fun arabicDeadlineKeepsDateAndEnglishTitleTogetherInTheButton() {
        instrumentation.runOnMainSync { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar")) }
        seed()
        val title = "Print place cards"
        val due = runBlocking {
            val id = app.store.dao.activeEvents().single().id
            (app.store.dao.items(id).first { it.title == title }.item().detail as Details.Task).due
        }
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            lateinit var date: String
            lateinit var dashboard: String
            activity.onActivity {
                val locale = it.resources.configuration.locales[0]
                assertEquals("ar", locale.language)
                date = com.planavora.app.ui.localDate(due, locale)
                dashboard = it.getString(R.string.command_centre)
            }
            waitFor(dashboard)
            val name = compose.onNodeWithText(title, useUnmergedTree = true)
            val deadline = compose.onNodeWithText(date, useUnmergedTree = true)
            name.assertExists()
            deadline.assertExists()
            assertTrue(name.getUnclippedBoundsInRoot().bottom <= deadline.getUnclippedBoundsInRoot().top)
            compose.onNode(hasClickAction() and hasText(title) and hasText(date)).performClick()
            waitFor(title)
        }
    }

    @Test fun capturesEightRealScreens() {
        val locale = InstrumentationRegistry.getArguments().getString("captureLocale") ?: "en-GB"
        instrumentation.runOnMainSync { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(locale)) }
        seed()
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            lateinit var appContext: Context
            activity.onActivity { appContext = it }
            fun label(id: Int) = appContext.getString(id)
            fun tapLabel(id: Int) = tap(label(id))
            fun capture(name: String, wait: String) {
                waitFor(wait)
                compose.waitForIdle()
                instrumentation.waitForIdleSync()
                val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                val destination = File(app.filesDir, "capture/$locale/$name.png")
                destination.parentFile?.mkdirs()
                destination.outputStream().use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            }
            capture("01-command-centre", label(R.string.command_centre))
            tapLabel(R.string.budget)
            capture("02-budget", label(R.string.category_spending))
            tapLabel(R.string.guests)
            capture("03-rsvps", "Rowan A.")
            tapLabel(R.string.more)
            tapLabel(R.string.assistant)
            capture("04-planning-assistant", label(R.string.local_assistant))
            tapLabel(R.string.back)
            tapLabel(R.string.voice)
            val example = appContext.resources.getStringArray(R.array.voice_balance).first()
            tap(example)
            tapLabel(R.string.review_command)
            val totals = budget(EventDetails(budget = 150000), runBlocking {
                app.store.dao.items(app.store.dao.activeEvents().single().id).map(ItemRow::item)
            })
            val money = Money(appContext.resources.configuration.locales[0], "GBP")
            capture("05-voice", appContext.getString(R.string.choice_value, label(R.string.remaining), money.display(totals.remaining)))
            tapLabel(R.string.back)
            tapLabel(R.string.vendors)
            capture("06-vendors", "Willow Kitchen")
            tapLabel(R.string.planning)
            tapLabel(R.string.timeline)
            capture("07-timeline", "Welcome toast")
            tapLabel(R.string.more)
            tapLabel(R.string.reports)
            capture("08-reports", label(R.string.share))
        }
    }

    @Test fun koreanAndChineseLoadTheirOwnResources() {
        val originalLocales = AppCompatDelegate.getApplicationLocales()
        try {
            val labels = mapOf("ko" to "설정", "zh-Hans" to "设置", "zh-Hant" to "設定")
            for ((tag, settings) in labels) {
                instrumentation.runOnMainSync {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                }
                ActivityScenario.launch(MainActivity::class.java).use { activity ->
                    activity.onActivity {
                        assertEquals(tag, it.resources.configuration.locales[0].toLanguageTag())
                        assertEquals(settings, it.getString(R.string.settings))
                    }
                }
            }
        } finally {
            instrumentation.runOnMainSync { AppCompatDelegate.setApplicationLocales(originalLocales) }
        }
    }

    private fun seed() = runBlocking {
        val store = app.store
        store.dao.deleteAll()
        val date = LocalDate.now().plusDays(26)
        val id = store.saveEvent(null, "Avery", EventDetails(type = EventType.BIRTHDAY, budget = 150000,
            date = date.toString(), time = "17:00", zone = "Europe/London", location = "Willow Garden"), false).second!!
        val due = date.minusDays(8).toString()
        suspend fun add(section: Section, name: String, detail: Details) {
            assertEquals(SaveResult.SAVED, store.saveItem(id, null, section, name, detail, true))
        }
        add(Section.TASK, "Reserve the garden", Details.Task(complete = true))
        add(Section.TASK, "Final menu selections", Details.Task(due = due))
        add(Section.TASK, "Print place cards", Details.Task(due = date.minusDays(4).toString()))
        add(Section.VENDOR, "Willow Kitchen", Details.Vendor("Willow Kitchen", quote = 46000, agreed = 44000, booked = true))
        val vendor = store.dao.items(id).last().id
        add(Section.PAYMENT, "Catering deposit", Details.Payment(vendor, 12000, paid = true, deposit = true))
        add(Section.PAYMENT, "Catering balance", Details.Payment(vendor, 32000, due))
        add(Section.EXPENSE, "Flowers and candles", Details.Expense(planned = 12500, committed = 12500, paid = 9900))
        add(Section.EXPENSE, "Table linen", Details.Expense(planned = 6800, committed = 6800, paid = 6800))
        add(Section.GUEST, "Rowan A.", Details.Guest(invited = true, rsvp = Rsvp.CONFIRMED, plusOnes = 1, headcount = 2))
        add(Section.GUEST, "Sage B.", Details.Guest(invited = true))
        add(Section.GUEST, "Morgan P.", Details.Guest(invited = true, rsvp = Rsvp.MAYBE))
        add(Section.TIMELINE, "Doors open", Details.Timeline("17:00", "17:30", location = "Willow Garden"))
        add(Section.TIMELINE, "Welcome toast", Details.Timeline("17:30", "17:50"))
        add(Section.TIMELINE, "Dinner", Details.Timeline("18:00", "19:30", vendorId = vendor))
    }
}
