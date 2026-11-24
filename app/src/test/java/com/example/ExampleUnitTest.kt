package com.example

import com.example.core.CalculatorEngine
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class ExampleUnitTest {
    @Test
    fun calculator_respectsOperatorPrecedence() {
        val res = CalculatorEngine.evaluate("2+3×4")
        assertTrue(res.isSuccess)
        assertEquals("14", CalculatorEngine.formatDecimal(res.getOrThrow()))

        val res2 = CalculatorEngine.evaluate("10−6÷2")
        assertTrue(res2.isSuccess)
        assertEquals("7", CalculatorEngine.formatDecimal(res2.getOrThrow()))
    }

    @Test
    fun calculator_handlesDecimalsAccurately() {
        val res = CalculatorEngine.evaluate("0.1+0.2")
        assertTrue(res.isSuccess)
        assertEquals("0.3", CalculatorEngine.formatDecimal(res.getOrThrow()))

        val res2 = CalculatorEngine.evaluate("12.5×4")
        assertTrue(res2.isSuccess)
        assertEquals("50", CalculatorEngine.formatDecimal(res2.getOrThrow()))
    }

    @Test
    fun calculator_handlesPercentageAndUnarySign() {
        val pct = CalculatorEngine.evaluate("200×15%")
        assertTrue(pct.isSuccess)
        assertEquals("30", CalculatorEngine.formatDecimal(pct.getOrThrow()))

        val neg = CalculatorEngine.evaluate("-5+8")
        assertTrue(neg.isSuccess)
        assertEquals("3", CalculatorEngine.formatDecimal(neg.getOrThrow()))

        val negMul = CalculatorEngine.evaluate("6×-3")
        assertTrue(negMul.isSuccess)
        assertEquals("-18", CalculatorEngine.formatDecimal(negMul.getOrThrow()))
    }

    @Test
    fun calculator_preventsDivisionByZeroAndInvalidSyntax() {
        val divZero = CalculatorEngine.evaluate("10÷0")
        assertTrue(divZero.isFailure)
        assertTrue(divZero.exceptionOrNull()?.message?.contains("zero", ignoreCase = true) == true)

        assertTrue(CalculatorEngine.evaluate("").isFailure)
        assertTrue(CalculatorEngine.evaluate("5+*2").isFailure)
        assertTrue(CalculatorEngine.evaluate("5.").isSuccess)
        assertTrue(CalculatorEngine.evaluate("5..2").isFailure)
    }

    @Test
    fun calculator_formatsLargeNumbersWithoutCrashing() {
        val big = BigDecimal("12345678901234567890")
        val formatted = CalculatorEngine.formatDecimal(big)
        assertTrue(formatted.isNotBlank())
    }
}

