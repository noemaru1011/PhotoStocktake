@file:Suppress("NonAsciiCharacters", "RemoveRedundantBackticks")

package com.example.photostocktake.domain

import com.example.photostocktake.model.PhotoItem
import com.example.photostocktake.model.PhotoStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SnoozeCalculatorTest {

    @Test
    fun `未整理（PENDING）の写真は再確認対象とする`() {
        val calculator = SnoozeCalculator { 10000L }
        val photo = PhotoItem(id = 1L, name = "test.jpg", dateTaken = 5000L, dateAdded = 5000L, status = PhotoStatus.PENDING)

        assertTrue(calculator.isDueForReview(photo))
    }

    @Test
    fun `保管（KEPT）および削除（DELETED）の写真は再確認対象外とする`() {
        val calculator = SnoozeCalculator { 10000L }
        val keptPhoto = PhotoItem(id = 1L, name = "test.jpg", dateTaken = 5000L, dateAdded = 5000L, status = PhotoStatus.KEPT)
        val deletedPhoto = PhotoItem(id = 2L, name = "test2.jpg", dateTaken = 5000L, dateAdded = 5000L, status = PhotoStatus.DELETED)

        assertFalse(calculator.isDueForReview(keptPhoto))
        assertFalse(calculator.isDueForReview(deletedPhoto))
    }

    @Test
    fun `再確認待ち（SNOOZED）の写真は設定日時を経過している場合のみ再確認対象とする`() {
        val now = 1000000L
        val calculator = SnoozeCalculator { now }

        val pastSnooze = PhotoItem(id = 1L, name = "p1.jpg", dateTaken = 5000L, dateAdded = 5000L, status = PhotoStatus.SNOOZED, reviewAt = 900000L)
        val futureSnooze = PhotoItem(id = 2L, name = "p2.jpg", dateTaken = 5000L, dateAdded = 5000L, status = PhotoStatus.SNOOZED, reviewAt = 1100000L)

        assertTrue(calculator.isDueForReview(pastSnooze))
        assertFalse(calculator.isDueForReview(futureSnooze))
    }

    @Test
    fun `翌日22時00分の日時タイムスタンプを正確に計算できる`() {
        // 現在日時を固定: 2026-03-01 10:00:00
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 1, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val calculator = SnoozeCalculator { cal.timeInMillis }

        val target = calculator.getTomorrowTargetTime()

        val expectedCal = Calendar.getInstance().apply {
            set(2026, Calendar.MARCH, 2, 22, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expectedCal.timeInMillis, target)
    }
}
