package no.nav.syfo.dialogmelding.domain

import no.nav.syfo.domain.PersonIdentNumber
import java.util.*

data class DialogmeldingSvar(
    val conversationRef: UUID?,
    val parentRef: UUID?,
    val arbeidstakerPersonIdent: PersonIdentNumber,
    val innkallingDialogmoteSvar: InnkallingDialogmoteSvar?,
)

data class InnkallingDialogmoteSvar(
    val foresporselType: ForesporselType,
    val svarType: SvarType,
    val svarTekst: String?
)

enum class ForesporselType {
    INNKALLING,
    ENDRING,
}

enum class SvarType {
    KOMMER,
    NYTT_TID_STED,
    KOMMER_IKKE,
}
