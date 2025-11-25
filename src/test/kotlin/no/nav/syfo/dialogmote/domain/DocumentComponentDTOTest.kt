package no.nav.syfo.dialogmote.domain

import no.nav.syfo.domain.dialogmote.DocumentComponentDTO
import no.nav.syfo.domain.dialogmote.DocumentComponentType
import no.nav.syfo.infrastructure.client.pdfgen.sanitizeForPdfGen
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DocumentComponentDTOTest {

    @Test
    fun `removes illegal characters`() {
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
        assertEquals(expectedDocument, documentWithIllegalChar.sanitizeForPdfGen())
    }
}
