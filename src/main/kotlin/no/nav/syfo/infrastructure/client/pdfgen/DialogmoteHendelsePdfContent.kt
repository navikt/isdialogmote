package no.nav.syfo.infrastructure.client.pdfgen

import no.nav.syfo.domain.dialogmote.DocumentComponentDTO

data class DialogmoteHendelsePdfContent(
    val mottakerNavn: String?,
    val mottakerFodselsnummer: String?,
    val datoSendt: String,
    val documentComponents: List<DocumentComponentDTO>,
)
