package com.planavora.app

import com.planavora.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class PlanTest {
    @Test fun reconcilesVendorPaymentsAndExpenseDeposits() {
        val entries = listOf(
            Item("venue", Section.VENDOR, "Venue", Details.Vendor(quote = 32_000, agreed = 30_000)),
            Item("deposit", Section.PAYMENT, "Deposit", Details.Payment("venue", 10_000, paid = true, deposit = true)),
            Item("venue-line", Section.EXPENSE, "Venue", Details.Expense(vendorId = "venue", planned = 30_000,
                committed = 30_000, paid = 10_000, deposit = 10_000)),
            Item("decor", Section.EXPENSE, "Decor", Details.Expense(planned = 5_000, committed = 5_000, paid = 4_000, deposit = 1_000)),
            Item("cancelled", Section.EXPENSE, "Car", Details.Expense(planned = 10_000, paid = 10_000, cancelled = true))
        )
        assertEquals(Budget(37_000, 14_000, 35_000, 20_000, 36_000), budget(EventDetails(budget = 50_000), entries))
    }

    @Test fun handlesLocaleDecimalPrecisionAndRejectsPartialParse() {
        assertEquals(1_234L, Money(Locale.FRANCE, "EUR").parse("12,34"))
        assertNull(Money(Locale.UK, "GBP").parse("12.345"))
        assertNull(Money(Locale.UK, "GBP").parse("12xyz"))
        assertNull(Money(Locale.UK, "GBP").parse("-1"))
        assertNull(Money(Locale.JAPAN, "JPY").parse("1.1"))
        assertEquals(1L, Money(Locale.JAPAN, "JPY").parse("1"))
        assertEquals(1_234L, Money(Locale.UK, "KWD").parse("1.234"))
    }

    @Test fun doesNotMarkPendingOrMaybeInvitationsReady() {
        val rows = Rsvp.entries.map { state -> Item(state.name, Section.GUEST, state.name, Details.Guest(invited = true, rsvp = state)) } +
            Item("draft", Section.GUEST, "Draft", Details.Guest())
        val result = readiness(EventDetails(readiness = setOf(Readiness.GUESTS)), rows)
        assertEquals(mapOf(Readiness.GUESTS to ReadinessCount(2, 4)), result)
        assertTrue(readiness(EventDetails(guestTracking = false, readiness = setOf(Readiness.GUESTS)), rows).isEmpty())
    }

    @Test fun detectsOverlapsButAllowsAdjacentEntries() {
        fun slot(id: String, begin: String, end: String) = Item(id, Section.TIMELINE, id, Details.Timeline(begin, end))
        assertEquals(setOf("arrival", "music"), timelineConflicts(listOf(
            slot("arrival", "10:00", "10:30"), slot("music", "10:20", "10:30"), slot("speech", "10:30", "11:00"))))
    }

    @Test fun archivedCopyRemapsAllReferences() {
        assertEquals("group-copy", (Details.Guest(groupId = "group").remap(mapOf("group" to "group-copy")) as Details.Guest).groupId)
        assertEquals("vendor-copy", (Details.Expense(vendorId = "vendor").remap(mapOf("vendor" to "vendor-copy")) as Details.Expense).vendorId)
    }
}
