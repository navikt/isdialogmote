package no.nav.syfo.brev.behandler.kafka

import no.nav.syfo.brev.behandler.domain.*
import no.nav.syfo.domain.PersonIdentNumber
import java.time.LocalDateTime
import java.util.*

data class KafkaDialogmeldingDTO(
    val msgId: String,
    val msgType: String,
    val navLogId: String,
    val mottattTidspunkt: LocalDateTime,
    val conversationRef: String?,
    val parentRef: String?,
    val personIdentPasient: String,
    val pasientAktoerId: String,
    val personIdentBehandler: String,
    val behandlerAktoerId: String,
    val legekontorOrgNr: String?,
    val legekontorHerId: String?,
    val legekontorReshId: String?,
    val legekontorOrgName: String,
    val legehpr: String?,
    val dialogmelding: Dialogmelding,
    val antallVedlegg: Int,
    val journalpostId: String,
    val fellesformatXML: String,
)

data class Dialogmelding(
    val id: String,
    val innkallingMoterespons: InnkallingMoterespons?,
    val foresporselFraSaksbehandlerForesporselSvar: ForesporselFraSaksbehandlerForesporselSvar?,
    val henvendelseFraLegeHenvendelse: HenvendelseFraLegeHenvendelse?,
    val navnHelsepersonell: String,
    val signaturDato: LocalDateTime
)

data class HenvendelseFraLegeHenvendelse(
    val temaKode: TemaKode,
    val tekstNotatInnhold: String,
    val dokIdNotat: String?,
    val foresporsel: Foresporsel?,
    val rollerRelatertNotat: RollerRelatertNotat?
)

data class InnkallingMoterespons(
    val temaKode: TemaKode,
    val tekstNotatInnhold: String?,
    val dokIdNotat: String?,
    val foresporsel: Foresporsel?
)

data class TemaKode(
    val kodeverkOID: String,
    val dn: String,
    val v: String,
    val arenaNotatKategori: String,
    val arenaNotatKode: String,
    val arenaNotatTittel: String
)

data class ForesporselFraSaksbehandlerForesporselSvar(
    val temaKode: TemaKode,
    val tekstNotatInnhold: String,
    val dokIdNotat: String?,
    val datoNotat: LocalDateTime?
)

data class Foresporsel(
    val typeForesp: TypeForesp,
    val sporsmal: String,
    val dokIdForesp: String?,
    val rollerRelatertNotat: RollerRelatertNotat?
)

data class RollerRelatertNotat(
    val rolleNotat: RolleNotat?,
    val person: Person?,
    val helsepersonell: Helsepersonell?
)

data class Helsepersonell(
    val givenName: String,
    val familyName: String
)

data class Person(
    val givenName: String,
    val familyName: String
)

data class RolleNotat(
    val s: String,
    val v: String
)

data class TypeForesp(
    val dn: String,
    val s: String,
    val v: String
)

fun KafkaDialogmeldingDTO.toDialogmeldingSvar(): DialogmeldingSvar = DialogmeldingSvar(
    conversationRef = this.conversationRef?.let { UUID.fromString(it) },
    parentRef = this.parentRef?.let { UUID.fromString(it) },
    arbeidstakerPersonIdent = PersonIdentNumber(this.personIdentPasient),
    innkallingDialogmoteSvar = this.dialogmelding.innkallingMoterespons?.toInnkallingDialogmoteSvar()
)

private fun InnkallingMoterespons.toInnkallingDialogmoteSvar(): InnkallingDialogmoteSvar? {
    val foresporselType = this.foresporsel?.typeForesp?.toForesporselType()
    val svarType = this.temaKode.toSvarType()
    return if (svarType != null && foresporselType != null) {
        InnkallingDialogmoteSvar(
            foresporselType = foresporselType,
            svarType = svarType,
            svarTekst = this.tekstNotatInnhold,
        )
    } else null
}

fun TypeForesp.toForesporselType(): ForesporselType? {
    return when (this.s) {
        "2.16.578.1.12.4.1.1.8125" -> { // Innkalling dialogmøte forespørsel kodeverk
            when (this.v) {
                "1" -> ForesporselType.INNKALLING
                "2" -> ForesporselType.ENDRING
                else -> null
            }
        }
        else -> null
    }
}

fun TemaKode.toSvarType(): SvarType? {
    return when (this.kodeverkOID) {
        "2.16.578.1.12.4.1.1.8126" -> { // Innkalling dialogmøte svar kodeverk
            when (this.v) {
                "1" -> SvarType.KOMMER
                "2" -> SvarType.NYTT_TID_STED
                "3" -> SvarType.KOMMER_IKKE
                else -> null
            }
        }
        else -> null
    }
}
