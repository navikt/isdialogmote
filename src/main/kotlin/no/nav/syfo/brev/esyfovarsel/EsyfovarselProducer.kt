package no.nav.syfo.brev.esyfovarsel

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.syfo.dialogmote.domain.DialogmoteSvarType
import java.io.Serializable
import java.util.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class EsyfovarselProducer(private val kafkaEsyfovarselProducer: KafkaProducer<String, EsyfovarselHendelse>) {
    fun sendVarselToEsyfovarsel(esyfovarselHendelse: EsyfovarselHendelse) {
        log.info("Skal sende hendelse til varselbus topic ${esyfovarselHendelse.type}")
        try {
            kafkaEsyfovarselProducer.send(
                ProducerRecord(
                    ESYFOVARSEL_TOPIC,
                    UUID.randomUUID().toString(),
                    esyfovarselHendelse,
                )
            ).get()
        } catch (e: Exception) {
            log.error("Exception was thrown when attempting to send hendelse esyfovarsel: ${e.message}")
            throw e
        }
    }

    companion object {
        private const val ESYFOVARSEL_TOPIC = "team-esyfo.varselbus"
        private val log = LoggerFactory.getLogger(EsyfovarselProducer::class.java)
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
sealed interface EsyfovarselHendelse : Serializable {
    val type: HendelseType
    var data: Any?
}

data class NarmesteLederHendelse(
    override var type: HendelseType,
    override var data: Any?,
    val narmesteLederFnr: String,
    val arbeidstakerFnr: String,
    val orgnummer: String
) : EsyfovarselHendelse

data class ArbeidstakerHendelse(
    override val type: HendelseType,
    override var data: Any?,
    val arbeidstakerFnr: String,
    val orgnummer: String?
) : EsyfovarselHendelse

data class VarselData(
    val journalpost: VarselDataJournalpost? = null,
    val narmesteLeder: VarselDataNarmesteLeder? = null,
    val motetidspunkt: VarselDataMotetidspunkt? = null,
    val dialogmoteSvar: VarselDataDialogmoteSvar? = null
)

data class VarselDataJournalpost(
    val uuid: String,
    val id: String?
)

data class VarselDataNarmesteLeder(
    val navn: String?
)

data class VarselDataMotetidspunkt(
    val tidspunkt: LocalDateTime
)

data class VarselDataDialogmoteSvar(
    val svar: DialogmoteSvarType
)

enum class HendelseType {
    NL_DIALOGMOTE_INNKALT,
    SM_DIALOGMOTE_INNKALT,
    NL_DIALOGMOTE_AVLYST,
    SM_DIALOGMOTE_AVLYST,
    NL_DIALOGMOTE_REFERAT,
    SM_DIALOGMOTE_REFERAT,
    NL_DIALOGMOTE_NYTT_TID_STED,
    SM_DIALOGMOTE_NYTT_TID_STED,
    SM_DIALOGMOTE_LEST,
    NL_DIALOGMOTE_SVAR,
}
