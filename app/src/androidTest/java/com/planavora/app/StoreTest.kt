package com.planavora.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.planavora.app.data.*
import com.planavora.app.domain.*
import com.planavora.app.platform.buildReport
import com.planavora.app.platform.LocalFiles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class StoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun persistsRecordsAndMaintainsRelations() = runBlocking {
        val name = "test-" + newId() + ".db"
        try {
            val database = open(name)
            val store = PlanStore(database)
            val event = store.saveEvent(null, "Birthday", EventDetails(budget = 30_000), false).second!!
            assertEquals(SaveResult.PREMIUM, store.saveEvent(null, "Other", EventDetails(), false).first)
            val group = Details.Seating(capacity = 1)
            assertEquals(SaveResult.SAVED, store.saveItem(event, null, Section.SEATING, "Garden table", group, true))
            val groupId = store.dao.items(event).single().id
            val guest = Details.Guest(invited = true, rsvp = Rsvp.CONFIRMED, groupId = groupId, contact = "guest@example.invalid")
            assertEquals(SaveResult.SAVED, store.saveItem(event, null, Section.GUEST, "Rowan", guest, false))
            assertEquals(SaveResult.CAPACITY, store.saveItem(event, null, Section.GUEST, "Sage", guest, false))
            assertEquals(SaveResult.LINKED, store.deleteItem(event, groupId))
            assertEquals(SaveResult.SAVED, store.addTasks(event, listOf("Reserve venue" to Details.Task())))
            assertEquals(SaveResult.DUPLICATE, store.addTasks(event, listOf("reserve venue" to Details.Task())))
            database.close()
            val reopened = open(name)
            val restored = PlanStore(reopened)
            assertEquals(3, restored.dao.items(event).size)
            val row = restored.dao.items(event).first { it.section == Section.GUEST.name }
            assertEquals("CONFIRMED", restored.dao.observeHistory(row.id).first().single().toValue)
            val copy = restored.duplicate(event, "Birthday copy", true).second!!
            val cloned = restored.dao.items(copy).map(ItemRow::item)
            assertEquals(cloned.first { it.section == Section.SEATING }.id,
                (cloned.first { it.section == Section.GUEST }.detail as Details.Guest).groupId)
            assertEquals(SaveResult.SAVED, restored.setArchived(event, true, false))
            assertEquals(SaveResult.PREMIUM, restored.setArchived(event, false, false))
            val report = buildReport(context, "Birthday", EventDetails(), cloned, null, false)
            val text = report.text()
            assertTrue(text.contains("Rowan"))
            assertFalse(text.contains("guest@example.invalid"))
            val csv = ByteArrayOutputStream().also { report.writeCsv(it, context) }.toString("UTF-8")
            assertTrue(csv.contains("\"Rowan\""))
            val pdf = ByteArrayOutputStream().also(report::writePdf)
            assertTrue(pdf.toByteArray().copyOf(4).contentEquals("%PDF".toByteArray()))
            restored.dao.deleteEvent(event)
            assertTrue(restored.dao.items(event).isEmpty())
            assertTrue(restored.dao.observeHistory(row.id).first().isEmpty())
            reopened.close()
        } finally { context.deleteDatabase(name) }
    }

    @Test fun blocksAttachmentPathTraversal() {
        assertTrue(runCatching { LocalFiles.file(context, "../planavora.db") }.isFailure)
    }

    private fun open(name: String) = androidx.room.Room.databaseBuilder(context, PlanDatabase::class.java, name).build()
}
