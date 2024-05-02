package no.nav.syfo.client.pdfgen

import no.nav.syfo.dialogmote.domain.DocumentComponentDTO

data class DialogmoteHendelsePdfContent(
    val mottakerNavn: String?,
    val mottakerFodselsnummer: String?,
    val datoSendt: String,
    val documentComponents: List<DocumentComponentDTO>,
)
