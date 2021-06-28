package no.nav.syfo.brev.arbeidstaker.brukernotifikasjon

import no.nav.brukernotifikasjon.schemas.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

const val BRUKERNOTIFIKASJON_OPPGAVE_TOPIC = "aapen-brukernotifikasjon-nyOppgave-v1"
const val BRUKERNOTIFIKASJON_DONE_TOPIC = "aapen-brukernotifikasjon-done-v1"

class BrukernotifikasjonProducer(
    private val kafkaProducerOppgave: KafkaProducer<Nokkel, Oppgave>,
    private val kafkaProducerDone: KafkaProducer<Nokkel, Done>,
) {
    fun sendOppgave(
        nokkel: Nokkel,
        oppgave: Oppgave,
    ) {
        try {
            kafkaProducerOppgave.send(
                ProducerRecord(
                    BRUKERNOTIFIKASJON_OPPGAVE_TOPIC,
                    nokkel,
                    oppgave,
                )
            ).get()
        } catch (e: Exception) {
            log.error("Exception was thrown when attempting to send Oppgave with id {}: ${e.message}", nokkel.getEventId())
            throw e
        }
    }

    fun sendDone(
        nokkel: Nokkel,
        done: Done,
    ) {
        try {
            kafkaProducerDone.send(
                ProducerRecord(
                    BRUKERNOTIFIKASJON_DONE_TOPIC,
                    nokkel,
                    done,
                )
            ).get()
        } catch (e: Exception) {
            log.error("Exception was thrown when attempting to send Done with id {}: ${e.message}", nokkel.getEventId())
            throw e
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(BrukernotifikasjonProducer::class.java)
    }
}
