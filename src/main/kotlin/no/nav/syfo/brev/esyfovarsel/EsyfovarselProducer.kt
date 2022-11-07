package no.nav.syfo.brev.esyfovarsel

import java.io.Serializable
import java.util.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class EsyfovarselProducer(private val kafkaEsyfovarselProducer: KafkaProducer<String, EsyfovarselHendelse>) {
    fun sendVarselToEsyfovarsel(esyfovarselHendelse: EsyfovarselHendelse) {
        try {
            kafkaEsyfovarselProducer.send(
                ProducerRecord(
                    ESYFOVARSEL_TOPIC,
                    UUID.randomUUID().toString(),
                    esyfovarselHendelse,
                )
            ).get()
        } catch (e: Exception) {
            log.error("Exception was thrown when attempting to send KDialogmoteStatusEndring with id {}: ${e.message}")
            throw e
        }
    }

    companion object {
        private const val ESYFOVARSEL_TOPIC = "team-esyfo.varselbus"
        private val log = LoggerFactory.getLogger(EsyfovarselProducer::class.java)
    }
}

sealed interface EsyfovarselHendelse : Serializable {
    val type: EsyfovarselHendelseType
    var data: Any?
}

data class EsyfovarselNarmesteLederHendelse(
    override val type: EsyfovarselHendelseType,
    override var data: Any?,
    val narmesteLederFnr: String,
    val narmesteLederNavn: String?,
    val arbeidstakerFnr: String,
    val orgnummer: String
) : EsyfovarselHendelse

data class ArbeidstakerHendelse(
    override val type: EsyfovarselHendelseType,
    override var data: Any?,
    val arbeidstakerFnr: String,
    val orgnummer: String?
) : EsyfovarselHendelse

enum class EsyfovarselHendelseType {
    NL_DIALOGMOTE_INNKALT,
    SM_DIALOGMOTE_INNKALT,
    NL_DIALOGMOTE_AVLYST,
    SM_DIALOGMOTE_AVLYST,
    NL_DIALOGMOTE_REFERAT,
    SM_DIALOGMOTE_REFERAT,
    NL_DIALOGMOTE_NYTT_TID_STED,
    SM_DIALOGMOTE_NYTT_TID_STED,
}
