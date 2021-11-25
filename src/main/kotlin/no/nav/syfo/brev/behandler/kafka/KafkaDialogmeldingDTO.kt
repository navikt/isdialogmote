package no.nav.syfo.brev.behandler.kafka

import java.time.LocalDateTime

data class KafkaDialogmeldingDTO(
    val msgId: String,
    val navLogId: String,
    val mottattTidspunkt: LocalDateTime,
    val conversationRef: String,
    val parentRef: String,
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
    val teamakode: TemaKode,
    val tekstNotatInnhold: String,
    val dokIdNotat: String?,
    val foresporsel: Foresporsel?,
    val rollerRelatertNotat: RollerRelatertNotat?
)

data class InnkallingMoterespons(
    val teamakode: TemaKode,
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
    val teamakode: TemaKode,
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
