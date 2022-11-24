package no.nav.syfo.testhelper.generator

import java.time.LocalDateTime
import java.util.*
import no.nav.syfo.dialogmelding.domain.ForesporselType
import no.nav.syfo.dialogmelding.domain.Kodeverk
import no.nav.syfo.dialogmelding.domain.SvarType
import no.nav.syfo.dialogmelding.kafka.Dialogmelding
import no.nav.syfo.dialogmelding.kafka.Foresporsel
import no.nav.syfo.dialogmelding.kafka.InnkallingMoterespons
import no.nav.syfo.dialogmelding.kafka.KafkaDialogmeldingDTO
import no.nav.syfo.dialogmelding.kafka.TemaKode
import no.nav.syfo.dialogmelding.kafka.TypeForesp
import no.nav.syfo.domain.PersonIdent

fun generateKafkaDialogmeldingDTO(
    msgId: String? = null,
    msgType: String,
    personIdentPasient: PersonIdent,
    personIdentBehandler: PersonIdent,
    conversationRef: String?,
    parentRef: String?,
    innkallingMoterespons: InnkallingMoterespons?
) = KafkaDialogmeldingDTO(
    msgId = msgId ?: UUID.randomUUID().toString(),
    msgType = msgType,
    navLogId = UUID.randomUUID().toString(),
    mottattTidspunkt = LocalDateTime.now(),
    conversationRef = conversationRef,
    parentRef = parentRef,
    personIdentBehandler = personIdentBehandler.value,
    personIdentPasient = personIdentPasient.value,
    dialogmelding = Dialogmelding(
        id = UUID.randomUUID().toString(),
        innkallingMoterespons = innkallingMoterespons,
    )
)

fun generateInnkallingMoterespons(
    foresporselType: ForesporselType?,
    svarType: SvarType,
    svarTekst: String?,
): InnkallingMoterespons = InnkallingMoterespons(
    TemaKode(
        kodeverkOID = Kodeverk.SvarInnkallingDialogmote.KODE,
        dn = "",
        v = svarType.toKodeverdi(),
    ),
    tekstNotatInnhold = svarTekst,
    foresporsel = foresporselType?.let {
        Foresporsel(
            TypeForesp(
                dn = "",
                s = Kodeverk.ForesporselInnkallingDialogmote.KODE,
                v = it.toKodeverdi(),
            )
        )
    }
)

private fun SvarType.toKodeverdi(): String {
    return when (this) {
        SvarType.KOMMER -> "1"
        SvarType.NYTT_TIDSPUNKT -> "2"
        SvarType.KAN_IKKE_KOMME -> "3"
    }
}

private fun ForesporselType.toKodeverdi(): String {
    return when (this) {
        ForesporselType.INNKALLING -> "1"
        ForesporselType.ENDRING -> "2"
    }
}
