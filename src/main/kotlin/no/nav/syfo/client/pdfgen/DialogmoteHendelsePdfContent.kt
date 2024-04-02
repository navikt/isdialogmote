package no.nav.syfo.client.pdfgen

import no.nav.syfo.dialogmote.domain.DocumentComponentDTO

data class DialogmoteHendelsePdfContent(
    val documentComponents: List<DocumentComponentDTO>,
)
