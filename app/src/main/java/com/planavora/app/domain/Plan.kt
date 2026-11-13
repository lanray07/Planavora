package com.planavora.app.domain

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.ParsePosition
import java.time.LocalDate
import java.time.LocalTime
import java.util.Currency
import java.util.Locale

enum class Section { TASK, GUEST, EXPENSE, VENDOR, PAYMENT, TIMELINE, SEATING, DOCUMENT }
enum class Rsvp { PENDING, CONFIRMED, MAYBE, DECLINED }
enum class EventType { BIRTHDAY, WEDDING, BABY_SHOWER, ANNIVERSARY, GRADUATION, ENGAGEMENT, REUNION, COMMUNITY, CUSTOM }
enum class Readiness { TASKS, GUESTS, VENDORS, BUDGET, TIMELINE }

@Serializable
data class EventDetails(
    val type: EventType = EventType.CUSTOM,
    val date: String = "",
    val time: String = "",
    val zone: String = "",
    val location: String = "",
    val venue: String = "",
    val budget: Long = 0,
    val currency: String = "GBP",
    val capacity: Int = 0,
    val notes: String = "",
    val cover: String = "",
    val rsvpDeadline: String = "",
    val guestTracking: Boolean = true,
    val readiness: Set<Readiness> = Readiness.entries.toSet()
)

@Serializable
sealed interface Details {
    val notes: String

    @Serializable
    data class Task(val category: String = "", val due: String = "", val priority: Int = 1,
                    val complete: Boolean = false, val attachment: String = "",
                    override val notes: String = "") : Details

    @Serializable
    data class Guest(val household: String = "", val contact: String = "", val invited: Boolean = false,
                     val rsvp: Rsvp = Rsvp.PENDING, val plusOnes: Int = 0, val headcount: Int = 1,
                     val dietary: String = "", val groupId: String = "",
                     override val notes: String = "") : Details

    @Serializable
    data class Expense(val category: String = "", val vendorId: String = "", val planned: Long = 0,
                       val committed: Long = 0, val paid: Long = 0, val deposit: Long = 0,
                       val due: String = "", val cancelled: Boolean = false,
                       val receipt: String = "", override val notes: String = "") : Details

    @Serializable
    data class Vendor(val business: String = "", val category: String = "", val phone: String = "",
                      val email: String = "", val website: String = "", val quote: Long = 0,
                      val agreed: Long = 0, val booked: Boolean = false, val attachment: String = "",
                      override val notes: String = "") : Details

    @Serializable
    data class Payment(val vendorId: String = "", val amount: Long = 0, val due: String = "",
                       val paid: Boolean = false, val deposit: Boolean = false, val method: String = "",
                       val receipt: String = "", override val notes: String = "") : Details

    @Serializable
    data class Timeline(val start: String = "", val end: String = "", val person: String = "",
                        val location: String = "", val vendorId: String = "", val complete: Boolean = false,
                        override val notes: String = "") : Details

    @Serializable
    data class Seating(val capacity: Int = 0, override val notes: String = "") : Details

    @Serializable
    data class Document(val attachment: String = "", override val notes: String = "") : Details
}

fun Details.references(): Set<String> = when (this) {
    is Details.Guest -> setOf(groupId)
    is Details.Expense -> setOf(vendorId)
    is Details.Payment -> setOf(vendorId)
    is Details.Timeline -> setOf(vendorId)
    else -> emptySet()
}.filterNot(String::isBlank).toSet()

fun Details.remap(ids: Map<String, String>): Details = when (this) {
    is Details.Guest -> copy(groupId = ids[groupId].orEmpty())
    is Details.Expense -> copy(vendorId = ids[vendorId].orEmpty())
    is Details.Payment -> copy(vendorId = ids[vendorId].orEmpty())
    is Details.Timeline -> copy(vendorId = ids[vendorId].orEmpty())
    else -> this
}

data class Item(val id: String, val section: Section, val title: String, val detail: Details)
data class Budget(val planned: Long, val spent: Long, val committed: Long, val vendorBalance: Long, val remaining: Long)

fun budget(event: EventDetails, items: List<Item>): Budget {
    val expenses = items.mapNotNull { it.detail as? Details.Expense }.filterNot { it.cancelled }
    val vendors = items.filter { it.section == Section.VENDOR }.associateBy { it.id }
    val payments = items.mapNotNull { it.detail as? Details.Payment }
    val expensesByVendor = expenses.groupBy(Details.Expense::vendorId)
    val paymentsByVendor = payments.groupBy(Details.Payment::vendorId)
    val standalone = expensesByVendor[""].orEmpty()
    var planned = standalone.sumOf { it.planned }
    var committed = standalone.sumOf { it.committed }
    var spent = standalone.sumOf { it.paid }
    var vendorBalance = 0L
    for ((id, item) in vendors) {
        val vendor = item.detail as Details.Vendor
        val linked = expensesByVendor[id].orEmpty()
        val schedule = paymentsByVendor[id].orEmpty()
        val agreed = maxOf(vendor.agreed, linked.sumOf { it.committed }, schedule.sumOf { it.amount })
        val paid = maxOf(linked.sumOf { it.paid }, schedule.filter { it.paid }.sumOf { it.amount })
        planned += maxOf(vendor.quote, linked.sumOf { it.planned })
        committed += agreed
        spent += paid
        vendorBalance += (agreed - paid).coerceAtLeast(0)
    }
    return Budget(planned, spent, committed, vendorBalance, event.budget - spent)
}

data class ReadinessCount(val done: Int, val total: Int)

fun readiness(event: EventDetails, items: List<Item>): Map<Readiness, ReadinessCount> {
    val tasks = items.mapNotNull { it.detail as? Details.Task }
    val guests = items.mapNotNull { it.detail as? Details.Guest }.filter { it.invited }
    val vendors = items.mapNotNull { it.detail as? Details.Vendor }
    val timeline = items.mapNotNull { it.detail as? Details.Timeline }
    val totals = budget(event, items)
    return mapOf(
        Readiness.TASKS to ReadinessCount(tasks.count { it.complete }, tasks.size),
        Readiness.GUESTS to ReadinessCount(guests.count { it.rsvp == Rsvp.CONFIRMED || it.rsvp == Rsvp.DECLINED }, guests.size),
        Readiness.VENDORS to ReadinessCount(vendors.count { it.booked }, vendors.size),
        Readiness.BUDGET to ReadinessCount(if (event.budget > 0 && event.budget >= maxOf(totals.planned, totals.committed, totals.spent)) 1 else 0, 1),
        Readiness.TIMELINE to ReadinessCount(timeline.count { it.start.isNotBlank() && it.end.isNotBlank() }, timeline.size)
    ).filterKeys { it in event.readiness && (event.guestTracking || it != Readiness.GUESTS) }
}

fun timelineConflicts(items: List<Item>): Set<String> {
    val entries = items.mapNotNull { item ->
        val slot = item.detail as? Details.Timeline ?: return@mapNotNull null
        val start = runCatching { LocalTime.parse(slot.start) }.getOrNull() ?: return@mapNotNull null
        val end = runCatching { LocalTime.parse(slot.end) }.getOrNull() ?: return@mapNotNull null
        Triple(item.id, start, end)
    }.sortedBy { it.second }
    return buildSet {
        entries.forEachIndexed { index, slot ->
            entries.drop(index + 1).takeWhile { it.second < slot.third }.forEach { other ->
                add(slot.first)
                add(other.first)
            }
        }
    }
}

fun dueDate(details: Details): LocalDate? {
    val raw = when (details) {
        is Details.Task -> details.due.takeUnless { details.complete }
        is Details.Expense -> details.due.takeUnless { details.cancelled || details.paid >= details.committed }
        is Details.Payment -> details.due.takeUnless { details.paid }
        else -> null
    }
    return raw?.takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

class Money(private val locale: Locale, code: String) {
    private val currency = Currency.getInstance(code)
    private val scale = currency.defaultFractionDigits.coerceAtLeast(0)

    fun parse(text: String): Long? = parse(text, false)
    fun parseOptional(text: String): Long? = parse(text, true)

    private fun parse(text: String, allowBlank: Boolean): Long? {
        val input = text.trim()
        if (input.isEmpty()) return if (allowBlank) 0 else null
        val format = NumberFormat.getNumberInstance(locale) as DecimalFormat
        format.isParseBigDecimal = true
        format.isGroupingUsed = false
        val position = ParsePosition(0)
        val value = format.parse(input, position) as? BigDecimal ?: return null
        if (position.index != input.length || value.signum() < 0 || value.scale() > scale) return null
        return runCatching { value.movePointRight(scale).longValueExact().takeIf { it <= 100_000_000_000L } }.getOrNull()
    }

    fun display(value: Long): String = NumberFormat.getCurrencyInstance(locale).apply {
        currency = this@Money.currency
        minimumFractionDigits = scale
        maximumFractionDigits = scale
    }.format(BigDecimal.valueOf(value, scale))

    fun input(value: Long): String = if (value == 0L) "" else NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        maximumFractionDigits = scale
        roundingMode = RoundingMode.UNNECESSARY
    }.format(BigDecimal.valueOf(value, scale))
}
