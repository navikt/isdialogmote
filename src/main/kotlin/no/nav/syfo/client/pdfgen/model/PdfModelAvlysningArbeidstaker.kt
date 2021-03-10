package no.nav.syfo.client.pdfgen.model

data class PdfModelAvlysningArbeidstaker(
    val avlysning: AvlysningArbeidstaker,
)

data class AvlysningArbeidstaker(
    val tidOgSted: AvlysningArbeidstakerTidOgSted,
)

data class AvlysningArbeidstakerTidOgSted(
    val sted: String,
)
