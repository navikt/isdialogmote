package no.nav.syfo.client.pdfgen.model

data class PdfModelEndringTidStedArbeidsgiver(
    val endring: EndringTidStedArbeidsgiver,
)

data class EndringTidStedArbeidsgiver(
    val tidOgSted: EndringTidStedArbeidsgiverTidOgSted,
)

data class EndringTidStedArbeidsgiverTidOgSted(
    val sted: String,
    val videoLink: String?
)
