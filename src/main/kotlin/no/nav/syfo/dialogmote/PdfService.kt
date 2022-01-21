package no.nav.syfo.dialogmote

import no.nav.syfo.application.database.DatabaseInterface
import no.nav.syfo.dialogmote.database.getPdf

class PdfService(
    private val database: DatabaseInterface,
) {
    fun getPdf(pdfId: Int): ByteArray {
        return database.getPdf(pdfId).pdf
    }
}
