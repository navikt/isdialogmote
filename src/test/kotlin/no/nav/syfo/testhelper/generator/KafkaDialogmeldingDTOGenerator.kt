package no.nav.syfo.testhelper.generator

import no.nav.syfo.dialogmelding.domain.*
import no.nav.syfo.dialogmelding.kafka.*
import no.nav.syfo.domain.PersonIdentNumber
import java.time.LocalDateTime
import java.util.*

fun generateKafkaDialogmeldingDTO(
    msgId: String? = null,
    msgType: String,
    personIdentPasient: PersonIdentNumber,
    personIdentBehandler: PersonIdentNumber,
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
