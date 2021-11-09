package no.nav.syfo.dialogmote.api.domain

import no.nav.syfo.dialogmote.domain.DocumentComponentDTO

data class AvlysDialogmoteDTO(
    val arbeidstaker: AvlysningDTO,
    val arbeidsgiver: AvlysningDTO,
    val behandler: AvlysningDTO?,
)

data class AvlysningDTO(
    val begrunnelse: String,
    val avlysning: List<DocumentComponentDTO>,
)
