package no.nav.syfo.domain

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import org.amshove.kluent.internal.assertFailsWith

object VirksomhetsnummerSpek : Spek({

    describe("Validation of virksomhetsnummer") {

        it("Validates correctly") {
            Virksomhetsnummer("123456785")
            Virksomhetsnummer("889640782")
            assertFailsWith(IllegalArgumentException::class) {
                Virksomhetsnummer("889640780")
            }
            assertFailsWith(IllegalArgumentException::class) {
                Virksomhetsnummer("889640781")
            }
            assertFailsWith(IllegalArgumentException::class) {
                Virksomhetsnummer("889640783")
            }
            assertFailsWith(IllegalArgumentException::class) {
                Virksomhetsnummer("889640784")
            }
            assertFailsWith(IllegalArgumentException::class) {
                Virksomhetsnummer("889640785")
            }
            assertFailsWith(IllegalArgumentException::class) {
                Virksomhetsnummer("889640786")
            }
            assertFailsWith(IllegalArgumentException::class) {
                Virksomhetsnummer("889640787")
            }
            assertFailsWith(IllegalArgumentException::class) {
                Virksomhetsnummer("889640788")
            }
            assertFailsWith(IllegalArgumentException::class) {
                Virksomhetsnummer("889640789")
            }
        }
    }
})
