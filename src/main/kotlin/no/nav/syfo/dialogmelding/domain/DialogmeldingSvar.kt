package no.nav.syfo.dialogmelding.domain

import no.nav.syfo.dialogmote.domain.DialogmoteSvarType
import no.nav.syfo.dialogmote.domain.MotedeltakerVarselType
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

enum class SvarType {
    KOMMER,
    NYTT_TIDSPUNKT,
    KAN_IKKE_KOMME,
}

fun SvarType.getDialogmoteSvarType(): DialogmoteSvarType {
    return when (this) {
        SvarType.KOMMER -> DialogmoteSvarType.KOMMER
        SvarType.NYTT_TIDSPUNKT -> DialogmoteSvarType.NYTT_TID_STED
        SvarType.KAN_IKKE_KOMME -> DialogmoteSvarType.KOMMER_IKKE
    }
}

enum class ForesporselType {
    INNKALLING,
    ENDRING,
}

fun ForesporselType.getVarselType(): MotedeltakerVarselType {
    return when (this) {
        ForesporselType.INNKALLING -> MotedeltakerVarselType.INNKALT
        ForesporselType.ENDRING -> MotedeltakerVarselType.NYTT_TID_STED
    }
}
