package no.nav.syfo.dialogmelding.kafka

import no.nav.syfo.dialogmelding.COUNT_CREATE_INNKALLING_DIALOGMOTE_SVAR_BEHANDLER_MISSING_FORESPORSEL
import no.nav.syfo.dialogmelding.domain.*
import no.nav.syfo.domain.PersonIdent
import java.time.LocalDateTime

data class KafkaDialogmeldingDTO(
    val msgId: String,
    val msgType: String,
    val navLogId: String,
    val mottattTidspunkt: LocalDateTime,
    val conversationRef: String?,
    val parentRef: String?,
    val personIdentPasient: String,
    val personIdentBehandler: String,
    val dialogmelding: Dialogmelding,
)

data class Dialogmelding(
    val id: String,
    val innkallingMoterespons: InnkallingMoterespons?,
)

data class InnkallingMoterespons(
    val temaKode: TemaKode,
    val tekstNotatInnhold: String?,
    val foresporsel: Foresporsel?
)

data class TemaKode(
    val kodeverkOID: String,
    val dn: String,
    val v: String,
)

data class Foresporsel(
    val typeForesp: TypeForesp,
)

data class TypeForesp(
    val dn: String,
    val s: String,
    val v: String
)

fun KafkaDialogmeldingDTO.toDialogmeldingSvarAlternativer(): List<DialogmeldingSvar> {
    val list = this.dialogmelding.innkallingMoterespons?.toInnkallingDialogmeldingSvarAlternativer() ?: emptyList()
    return list.map { innkallingDialogmoteSvar ->
        DialogmeldingSvar(
            conversationRef = this.conversationRef,
            parentRef = this.parentRef,
            arbeidstakerPersonIdent = PersonIdent(this.personIdentPasient),
            behandlerPersonIdent = PersonIdent(this.personIdentBehandler),
            innkallingDialogmoteSvar = innkallingDialogmoteSvar,
        )
    }
}

private fun InnkallingMoterespons.toInnkallingDialogmeldingSvarAlternativer(): List<InnkallingDialogmoteSvar> {
    val foresporselType = this.foresporsel?.typeForesp?.toForesporselType()
    val svarType = this.temaKode.toSvarType()
    return if (svarType != null && foresporselType != null) {
        listOf(
            InnkallingDialogmoteSvar(
                foresporselType = foresporselType,
                svarType = svarType,
                svarTekst = this.tekstNotatInnhold,
            ),
        )
    } else if (svarType != null) {
        COUNT_CREATE_INNKALLING_DIALOGMOTE_SVAR_BEHANDLER_MISSING_FORESPORSEL.increment()
        listOf(
            InnkallingDialogmoteSvar(
                foresporselType = ForesporselType.ENDRING,
                svarType = svarType,
                svarTekst = this.tekstNotatInnhold,
            ),
            InnkallingDialogmoteSvar(
                foresporselType = ForesporselType.INNKALLING,
                svarType = svarType,
                svarTekst = this.tekstNotatInnhold,
            ),
        )
    } else emptyList()
}

fun TypeForesp.toForesporselType(): ForesporselType? {
    return when (this.s) {
        Kodeverk.ForesporselInnkallingDialogmote.KODE -> {
            when (this.v) {
                Kodeverk.ForesporselInnkallingDialogmote.INNKALLING -> ForesporselType.INNKALLING
                Kodeverk.ForesporselInnkallingDialogmote.ENDRING -> ForesporselType.ENDRING
                else -> null
            }
        }
        else -> null
    }
}

fun TemaKode.toSvarType(): SvarType? {
    return when (this.kodeverkOID) {
        Kodeverk.SvarInnkallingDialogmote.KODE -> {
            when (this.v) {
                Kodeverk.SvarInnkallingDialogmote.KOMMER -> SvarType.KOMMER
                Kodeverk.SvarInnkallingDialogmote.NYTT_TIDSPUNKT -> SvarType.NYTT_TIDSPUNKT
                Kodeverk.SvarInnkallingDialogmote.KAN_IKKE_KOMME -> SvarType.KAN_IKKE_KOMME
                else -> null
            }
        }
        else -> null
    }
}
