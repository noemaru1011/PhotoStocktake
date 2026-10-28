@file:Suppress("NonAsciiCharacters", "RemoveRedundantBackticks")

package com.example.photostocktake.domain

import com.example.photostocktake.model.PhotoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BurstGroupingUseCaseTest {

    private val useCase = BurstGroupingUseCase(thresholdMs = 2000L)

    @Test
    fun `入力が空のリストの場合は空のリストを返す`() {
        val result = useCase.execute(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `写真が1枚のみの場合は単一要素のグループを1つ作成する`() {
        val photo = PhotoItem(id = 1L, name = "p1.jpg", dateTaken = 1000L, dateAdded = 1000L)
        val result = useCase.execute(listOf(photo))

        assertEquals(1, result.size)
        assertEquals(1, result[0].size)
        assertEquals(1L, result[0][0].id)
    }

    @Test
    fun `撮影日時順に並んでいない写真も日時順にグループ化する`() {
        val older = PhotoItem(id = 1L, name = "older.jpg", dateTaken = 1000L, dateAdded = 1000L)
        val newer = PhotoItem(id = 2L, name = "newer.jpg", dateTaken = 3000L, dateAdded = 3000L)

        val result = useCase.execute(listOf(newer, older))

        assertEquals(listOf(1L, 2L), result.first().map { it.id })
    }

    @Test
    fun `ちょうど2秒差の写真は同じグループにまとめる`() {
        val first = PhotoItem(id = 1L, name = "first.jpg", dateTaken = 1000L, dateAdded = 1000L)
        val second = PhotoItem(id = 2L, name = "second.jpg", dateTaken = 3000L, dateAdded = 3000L)

        val result = useCase.execute(listOf(first, second))

        assertEquals(1, result.size)
        assertEquals(listOf(1L, 2L), result[0].map { it.id })
    }

    @Test
    fun `連続して2秒を超える間隔があると別グループにする`() {
        val p1 = PhotoItem(id = 1L, name = "p1.jpg", dateTaken = 1000L, dateAdded = 1000L)
        val p2 = PhotoItem(id = 2L, name = "p2.jpg", dateTaken = 2500L, dateAdded = 2500L) // 1500ms差 -> 同一グループ
        val p3 = PhotoItem(id = 3L, name = "p3.jpg", dateTaken = 6000L, dateAdded = 6000L) // 3500ms差 -> 別グループ

        val result = useCase.execute(listOf(p1, p2, p3))

        assertEquals(2, result.size)
        // 最新撮影日時の降順でソート -> グループ2 (p3) が先頭、次にグループ1 (p1, p2)
        assertEquals(1, result[0].size)
        assertEquals(3L, result[0][0].id)

        assertEquals(2, result[1].size)
        assertEquals(1L, result[1][0].id)
        assertEquals(2L, result[1][1].id)
    }
}
