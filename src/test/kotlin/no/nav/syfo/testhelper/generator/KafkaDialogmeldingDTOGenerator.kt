package no.nav.syfo.testhelper.generator

import no.nav.syfo.domain.ForesporselType
import no.nav.syfo.domain.Kodeverk
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.SvarType
import no.nav.syfo.infrastructure.kafka.dialogmelding.Dialogmelding
import no.nav.syfo.infrastructure.kafka.dialogmelding.Foresporsel
import no.nav.syfo.infrastructure.kafka.dialogmelding.InnkallingMoterespons
import no.nav.syfo.infrastructure.kafka.dialogmelding.KafkaDialogmeldingDTO
import no.nav.syfo.infrastructure.kafka.dialogmelding.TemaKode
import no.nav.syfo.infrastructure.kafka.dialogmelding.TypeForesp
import java.time.LocalDateTime
import java.util.*

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
        signaturDato = LocalDateTime.now(),
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
