package no.nav.syfo.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VirksomhetsnummerTest {

    @Test
    fun `Validates correctly`() {
        Virksomhetsnummer("123456785")
        Virksomhetsnummer("889640782")
        assertThrows<IllegalArgumentException> {
            Virksomhetsnummer("889640780")
        }
        assertThrows<IllegalArgumentException> {
            Virksomhetsnummer("889640781")
        }
        assertThrows<IllegalArgumentException> {
            Virksomhetsnummer("889640783")
        }
        assertThrows<IllegalArgumentException> {
            Virksomhetsnummer("889640784")
        }
        assertThrows<IllegalArgumentException> {
            Virksomhetsnummer("889640785")
        }
        assertThrows<IllegalArgumentException> {
            Virksomhetsnummer("889640786")
        }
        assertThrows<IllegalArgumentException> {
            Virksomhetsnummer("889640787")
        }
        assertThrows<IllegalArgumentException> {
            Virksomhetsnummer("889640788")
        }
        assertThrows<IllegalArgumentException> {
            Virksomhetsnummer("889640789")
        }
    }
}
