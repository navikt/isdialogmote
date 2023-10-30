package no.nav.syfo.dialogmote.domain

import no.nav.syfo.client.pdfgen.sanitizeForPdfGen
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class DocumentComponentDTOSpek : Spek({
    describe("sanitizeForPdfGen") {
        it("removes illegal characters") {
            val documentWithIllegalChar = listOf(
                DocumentComponentDTO(
                    type = DocumentComponentType.PARAGRAPH,
                    title = "tittel",
                    texts = listOf("text1\u0002dsa", "text2"),
                )
            )
            val expectedDocument = listOf(
                DocumentComponentDTO(
                    type = DocumentComponentType.PARAGRAPH,
                    title = "tittel",
                    texts = listOf("text1dsa", "text2"),
                )
            )
            documentWithIllegalChar.sanitizeForPdfGen() shouldBeEqualTo expectedDocument
        }
    }
})