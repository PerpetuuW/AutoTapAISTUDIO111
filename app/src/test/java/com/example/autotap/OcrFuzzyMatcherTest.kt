package com.example.autotap

import com.example.autotap.infrastructure.ocr.OcrEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrFuzzyMatcherTest {

    @Test
    fun testLevenshteinDistance_CalculatesEditDistanceCorrectly() {
        assertEquals(0, OcrEngine.levenshteinDistance("альянс", "альянс"))
        assertEquals(1, OcrEngine.levenshteinDistance("алянс", "альянс")) // 1 эрозия/пропуск
        assertEquals(1, OcrEngine.levenshteinDistance("альяис", "альянс")) // 1 замена
        assertEquals(1, OcrEngine.levenshteinDistance("альянсо", "альянс")) // 1 вставка
        assertEquals(2, OcrEngine.levenshteinDistance("ольямс", "альянс")) // 2 замены
    }

    @Test
    fun testIsFuzzyMatch_SoftSignOmission_MatchesSuccessfully() {
        // Лог пользователя: 'алянс' вместо 'альянс'
        val method = OcrEngine::class.java.getDeclaredMethod("isFuzzyMatch", String::class.java, String::class.java)
        method.isAccessible = true

        val isMatch = method.invoke(OcrEngine, "алянс", "альянс") as Boolean
        assertTrue("Пропущенный мягкий знак в 'алянс' обязан совпадать с 'альянс'", isMatch)
    }

    @Test
    fun testIsFuzzyMatch_CyrillicHomoglyphSubstitution_MatchesSuccessfully() {
        val method = OcrEngine::class.java.getDeclaredMethod("isFuzzyMatch", String::class.java, String::class.java)
        method.isAccessible = true

        val match1 = method.invoke(OcrEngine, "альяис", "альянс") as Boolean
        val match2 = method.invoke(OcrEngine, "АЛЬЯНС", "альянс") as Boolean
        val match3 = method.invoke(OcrEngine, "Aльянс", "альянс") as Boolean

        assertTrue("Путаница 'и' <-> 'н' обязана совпадать", match1)
        assertTrue("Верхний регистр 'АЛЬЯНС' обязан совпадать", match2)
        assertTrue("Латинская 'A' в 'Aльянс' обязана совпадать", match3)
    }

    @Test
    fun testIsFuzzyMatch_SingleLetterCandidate_DoesNotMatchLongQuery() {
        // Защита от ложного срабатывания, когда одиночная буква 'з' совпала с 'альянс'
        val method = OcrEngine::class.java.getDeclaredMethod("isFuzzyMatch", String::class.java, String::class.java)
        method.isAccessible = true

        val falseMatch = method.invoke(OcrEngine, "з", "альянс") as Boolean
        assertFalse("Одиночная буква 'з' НЕ должна совпадать со словом 'альянс'", falseMatch)
    }
}
