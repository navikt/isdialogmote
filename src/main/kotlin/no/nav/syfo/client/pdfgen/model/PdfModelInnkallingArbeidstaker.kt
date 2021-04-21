package no.nav.syfo.client.pdfgen.model

data class PdfModelInnkallingArbeidstaker(
    val innkalling: InnkallingArbeidstaker,
)

data class InnkallingArbeidstaker(
    val tidOgSted: InnkallingArbeidstakerTidOgSted,
)

data class InnkallingArbeidstakerTidOgSted(
    val sted: String,
    val videoLink: String
)
