package me.mjunaid.mjpay.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvTest {

    @Test
    fun plainCellIsUnchanged() {
        assertEquals("bKash", Csv.escapeCell("bKash"))
    }

    @Test
    fun commaForcesQuotes() {
        assertEquals("\"Tk, 500\"", Csv.escapeCell("Tk, 500"))
    }

    @Test
    fun quotesAreDoubled() {
        assertEquals("\"a\"\"b\"", Csv.escapeCell("a\"b"))
    }

    @Test
    fun newlinesForceQuotes() {
        assertEquals("\"a\nb\"", Csv.escapeCell("a\nb"))
        assertEquals("\"a\rb\"", Csv.escapeCell("a\rb"))
    }

    @Test
    fun formulaInjectionIsNeutralized() {
        assertEquals("'=1+1", Csv.escapeCell("=1+1"))
        assertEquals("'+1", Csv.escapeCell("+1"))
        assertEquals("'-1", Csv.escapeCell("-1"))
        assertEquals("'@cmd", Csv.escapeCell("@cmd"))
        assertEquals("'\tx", Csv.escapeCell("\tx"))
    }

    @Test
    fun dangerousCellWithCommaGetsBothProtections() {
        assertEquals("\"'=SUM(A1,B1)\"", Csv.escapeCell("=SUM(A1,B1)"))
    }

    @Test
    fun rowJoinsEscapedCells() {
        assertEquals("a,\"b,c\",d", Csv.row("a", "b,c", "d"))
    }

    @Test
    fun emptyCellsStayEmpty() {
        assertEquals(",,", Csv.row("", "", ""))
    }
}
