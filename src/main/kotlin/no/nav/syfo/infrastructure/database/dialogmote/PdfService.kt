package no.nav.syfo.infrastructure.database.dialogmote

import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.dialogmote.database.getPdf

class PdfService(
    private val database: DatabaseInterface,
) {
    fun getPdf(pdfId: Int): ByteArray {
        return database.getPdf(pdfId).pdf
    }
}
