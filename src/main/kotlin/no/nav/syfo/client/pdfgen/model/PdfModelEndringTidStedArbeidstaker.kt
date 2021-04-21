package no.nav.syfo.client.pdfgen.model

data class PdfModelEndringTidStedArbeidstaker(
    val endring: EndringTidStedArbeidstaker,
)

data class EndringTidStedArbeidstaker(
    val tidOgSted: EndringTidStedArbeidstakerTidOgSted,
)

data class EndringTidStedArbeidstakerTidOgSted(
    val sted: String,
    val videoLink: String?
)
