package no.nav.syfo.client.pdfgen.model

data class PdfModelAvlysningArbeidsgiver(
    val avlysning: AvlysningArbeidsgiver,
)

data class AvlysningArbeidsgiver(
    val tidOgSted: AvlysningArbeidsgiverTidOgSted,
)

data class AvlysningArbeidsgiverTidOgSted(
    val sted: String,
)
