package no.nav.syfo.brev.arbeidstaker.brukernotifikasjon

import no.nav.brukernotifikasjon.schemas.input.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

const val BRUKERNOTIFIKASJON_BESKJED_TOPIC = "min-side.aapen-brukernotifikasjon-beskjed-v1"
const val BRUKERNOTIFIKASJON_OPPGAVE_TOPIC = "min-side.aapen-brukernotifikasjon-oppgave-v1"
const val BRUKERNOTIFIKASJON_DONE_TOPIC = "min-side.aapen-brukernotifikasjon-done-v1"

class BrukernotifikasjonProducer(
    private val kafkaProducerBeskjed: KafkaProducer<NokkelInput, BeskjedInput>,
    private val kafkaProducerOppgave: KafkaProducer<NokkelInput, OppgaveInput>,
    private val kafkaProducerDone: KafkaProducer<NokkelInput, DoneInput>,
) {
    fun sendBeskjed(
        nokkel: NokkelInput,
        beskjed: BeskjedInput,
    ) {
        try {
            kafkaProducerBeskjed.send(
                ProducerRecord(
                    BRUKERNOTIFIKASJON_BESKJED_TOPIC,
                    nokkel,
                    beskjed,
                )
            ).get()
        } catch (e: Exception) {
            log.error("Exception was thrown when attempting to send Beskjed with id {}: ${e.message}", nokkel.getEventId())
            throw e
        }
    }

    fun sendOppgave(
        nokkel: NokkelInput,
        oppgave: OppgaveInput,
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
        nokkel: NokkelInput,
        done: DoneInput,
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
