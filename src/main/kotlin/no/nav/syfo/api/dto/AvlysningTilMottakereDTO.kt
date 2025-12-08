package no.nav.syfo.api.dto

import no.nav.syfo.domain.dialogmote.DocumentComponentDTO

data class AvlysningTilMottakereDTO(
    val arbeidstaker: AvlysningDTO,
    val arbeidsgiver: AvlysningDTO,
    val behandler: AvlysningDTO?,
)

data class AvlysningDTO(
    val begrunnelse: String,
    val avlysning: List<DocumentComponentDTO>,
)
