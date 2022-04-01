package no.nav.syfo.brev.narmesteleder.dinesykmeldte

import no.nav.syfo.brev.narmesteleder.domain.DineSykmeldteHendelse
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

const val DINESYKMELDTE_HENDELSE_TOPIC = "teamsykmelding.dinesykmeldte-hendelser-v2"

class DineSykmeldteVarselProducer(
    private val kafkaProducerVarsel: KafkaProducer<String, DineSykmeldteHendelse>,
) {
    fun sendDineSykmeldteVarsel(
        uuid: String,
        dineSykmeldteHendelse: DineSykmeldteHendelse,
    ) {
        try {
            kafkaProducerVarsel.send(
                ProducerRecord(
                    DINESYKMELDTE_HENDELSE_TOPIC,
                    uuid,
                    dineSykmeldteHendelse,
                )
            ).get()
        } catch (e: Exception) {
            log.error("Exception was thrown when attempting to send varsel with uuid {}: ${e.message}", uuid)
            throw e
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DineSykmeldteVarselProducer::class.java)
    }
}
