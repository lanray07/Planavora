package com.planavora.app.data

import android.content.Context
import androidx.room.*
import com.planavora.app.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.util.UUID

@Entity(tableName = "events")
data class EventRow(@PrimaryKey val id: String, val title: String, val archived: Boolean, val payload: String) {
    fun details(): EventDetails = codec.decodeFromString(payload)
}

@Entity(
    tableName = "items",
    foreignKeys = [ForeignKey(entity = EventRow::class, parentColumns = ["id"], childColumns = ["eventId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("eventId", "section")]
)
data class ItemRow(@PrimaryKey val id: String, val eventId: String, val section: String, val title: String,
                   val payload: String, val position: Long) {
    fun item(): Item = Item(id, Section.valueOf(section), title, codec.decodeFromString<Details>(payload))
}

@Entity(
    tableName = "history",
    foreignKeys = [ForeignKey(entity = ItemRow::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId")]
)
data class HistoryRow(@PrimaryKey val id: String, val itemId: String, val instant: Long, val fromValue: String, val toValue: String)

@Dao
interface PlanDao {
    @Query("SELECT * FROM events ORDER BY archived, title COLLATE NOCASE, id") fun observeEvents(): Flow<List<EventRow>>
    @Query("SELECT * FROM events WHERE archived = 0") suspend fun activeEvents(): List<EventRow>
    @Query("SELECT * FROM events WHERE id = :id") suspend fun event(id: String): EventRow?
    @Query("SELECT * FROM items WHERE eventId = :id ORDER BY position, id") fun observeItems(id: String): Flow<List<ItemRow>>
    @Query("SELECT * FROM items WHERE eventId = :id ORDER BY position, id") suspend fun items(id: String): List<ItemRow>
    @Query("SELECT MAX(position) FROM items WHERE eventId = :id") suspend fun lastPosition(id: String): Long?
    @Query("SELECT * FROM history WHERE itemId = :id ORDER BY instant DESC") fun observeHistory(id: String): Flow<List<HistoryRow>>
    @Upsert suspend fun put(event: EventRow)
    @Upsert suspend fun put(item: ItemRow)
    @Insert suspend fun add(history: HistoryRow)
    @Query("DELETE FROM items WHERE id = :id") suspend fun deleteItem(id: String)
    @Query("DELETE FROM events WHERE id = :id") suspend fun deleteEvent(id: String)
    @Query("DELETE FROM events") suspend fun deleteAll()
}

@Database(entities = [EventRow::class, ItemRow::class, HistoryRow::class], version = 1, exportSchema = true)
abstract class PlanDatabase : RoomDatabase() {
    abstract fun dao(): PlanDao
    companion object {
        fun open(context: Context) = Room.databaseBuilder(context, PlanDatabase::class.java, "planavora.db").build()
    }
}

val codec = Json { ignoreUnknownKeys = true }
fun newId(): String = UUID.randomUUID().toString()

enum class SaveResult { SAVED, PREMIUM, MISSING, CAPACITY, CURRENCY, LINKED, DUPLICATE }

class PlanStore(private val db: PlanDatabase) {
    val dao = db.dao()

    suspend fun saveEvent(id: String?, title: String, details: EventDetails, premium: Boolean): Pair<SaveResult, String?> =
        db.withTransaction {
            val previous = id?.let { dao.event(it) }
            if (id != null && previous == null) return@withTransaction SaveResult.MISSING to null
            if (previous == null && !premium && dao.activeEvents().isNotEmpty()) return@withTransaction SaveResult.PREMIUM to null
            if (previous != null && previous.details().currency != details.currency &&
                dao.items(previous.id).any { it.section in setOf("EXPENSE", "VENDOR", "PAYMENT") }) {
                return@withTransaction SaveResult.CURRENCY to null
            }
            val row = EventRow(id ?: newId(), title, previous?.archived ?: false, codec.encodeToString(details))
            dao.put(row)
            SaveResult.SAVED to row.id
        }

    suspend fun setArchived(id: String, archived: Boolean, premium: Boolean): SaveResult = db.withTransaction {
        val event = dao.event(id) ?: return@withTransaction SaveResult.MISSING
        if (!archived && !premium && dao.activeEvents().any { it.id != id }) return@withTransaction SaveResult.PREMIUM
        dao.put(event.copy(archived = archived))
        SaveResult.SAVED
    }

    suspend fun duplicate(id: String, title: String, premium: Boolean): Pair<SaveResult, String?> = db.withTransaction {
        if (!premium) return@withTransaction SaveResult.PREMIUM to null
        val event = dao.event(id) ?: return@withTransaction SaveResult.MISSING to null
        val copyId = newId()
        val rows = dao.items(id)
        val ids = rows.associate { it.id to newId() }
        dao.put(event.copy(id = copyId, title = title, archived = false))
        for (row in rows) {
            val draft = row.item().detail.remap(ids)
            dao.put(row.copy(id = ids.getValue(row.id), eventId = copyId, payload = codec.encodeToString<Details>(draft)))
        }
        SaveResult.SAVED to copyId
    }

    suspend fun saveItem(eventId: String, id: String?, section: Section, title: String, detail: Details,
                         premium: Boolean): SaveResult = db.withTransaction {
        if (dao.event(eventId) == null) return@withTransaction SaveResult.MISSING
        val rows = dao.items(eventId)
        val old = rows.firstOrNull { it.id == id }
        if (id != null && old == null) return@withTransaction SaveResult.MISSING
        if (old == null && !premium && (section in setOf(Section.PAYMENT, Section.SEATING) ||
                    (section == Section.GUEST && rows.count { it.section == section.name } >= 30))) {
            return@withTransaction SaveResult.PREMIUM
        }
        val linkedSection = when (detail) {
            is Details.Guest -> Section.SEATING
            else -> Section.VENDOR
        }
        if (detail.references().any { ref -> rows.none { it.id == ref && it.section == linkedSection.name } }) {
            return@withTransaction SaveResult.MISSING
        }
        val guests = rows.filter { it.id != id }.mapNotNull { it.item().detail as? Details.Guest }
        if (detail is Details.Guest && detail.groupId.isNotBlank()) {
            val group = rows.first { it.id == detail.groupId }.item().detail as Details.Seating
            if (group.capacity > 0 && guests.filter { it.groupId == detail.groupId }.sumOf { it.headcount } + detail.headcount > group.capacity) {
                return@withTransaction SaveResult.CAPACITY
            }
        }
        if (detail is Details.Seating && detail.capacity > 0 &&
            guests.filter { it.groupId == id }.sumOf { it.headcount } > detail.capacity) return@withTransaction SaveResult.CAPACITY
        val itemId = id ?: newId()
        val position = old?.position ?: ((dao.lastPosition(eventId) ?: 0) + 1)
        dao.put(ItemRow(itemId, eventId, section.name, title, codec.encodeToString<Details>(detail), position))
        val previous = when (val previousDetail = old?.item()?.detail) {
            is Details.Task -> previousDetail.complete.toString()
            is Details.Guest -> previousDetail.rsvp.name
            is Details.Payment -> previousDetail.paid.toString()
            else -> null
        }
        val next = when (detail) {
            is Details.Task -> detail.complete.toString()
            is Details.Guest -> detail.rsvp.name
            is Details.Payment -> detail.paid.toString()
            else -> null
        }
        if (next != null && previous != next) {
            dao.add(HistoryRow(newId(), itemId, System.currentTimeMillis(), previous.orEmpty(), next))
        }
        SaveResult.SAVED
    }

    suspend fun deleteItem(eventId: String, id: String): SaveResult = db.withTransaction {
        if (dao.items(eventId).any { id in it.item().detail.references() }) return@withTransaction SaveResult.LINKED
        dao.deleteItem(id)
        SaveResult.SAVED
    }

    suspend fun addTasks(eventId: String, draft: List<Pair<String, Details.Task>>): SaveResult = db.withTransaction {
        if (dao.event(eventId) == null) return@withTransaction SaveResult.MISSING
        val existing = dao.items(eventId).filter { it.section == Section.TASK.name }
            .map { it.title.trim().lowercase() }.toMutableSet()
        val additions = draft.filter { existing.add(it.first.trim().lowercase()) }
        if (additions.isEmpty()) return@withTransaction SaveResult.DUPLICATE
        val start = dao.lastPosition(eventId) ?: 0
        additions.forEachIndexed { index, (title, detail) ->
            dao.put(ItemRow(newId(), eventId, Section.TASK.name, title, codec.encodeToString<Details>(detail), start + index + 1))
        }
        SaveResult.SAVED
    }
}
