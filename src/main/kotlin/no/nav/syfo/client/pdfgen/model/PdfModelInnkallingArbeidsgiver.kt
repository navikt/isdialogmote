package no.nav.syfo.client.pdfgen.model

data class PdfModelInnkallingArbeidsgiver(
    val innkalling: InnkallingArbeidsgiver,
)

data class InnkallingArbeidsgiver(
    val tidOgSted: InnkallingArbeidsgiverTidOgSted,
)

data class InnkallingArbeidsgiverTidOgSted(
    val sted: String,
    val videoLink: String?
)
