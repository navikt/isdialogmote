package no.nav.syfo.infrastructure.kafka.behandler

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class BehandlerDialogmeldingProducer(
    private val kafkaProducerBehandlerDialogmeldingBestilling: KafkaProducer<String, KafkaBehandlerDialogmeldingDTO>,
) {
    fun sendDialogmelding(
        dialogmelding: KafkaBehandlerDialogmeldingDTO,
    ) {
        try {
            kafkaProducerBehandlerDialogmeldingBestilling.send(
                ProducerRecord(
                    DIALOGMELDING_BESTILLING_TOPIC,
                    dialogmelding.dialogmeldingUuid,
                    dialogmelding,
                )
            ).get()
        } catch (e: Exception) {
            log.error(
                "Exception was thrown when attempting to send dialogmelding with id {}: ${e.message}",
                dialogmelding.dialogmeldingUuid,
                e
            )
            throw e
        }
    }

    companion object {
        const val DIALOGMELDING_BESTILLING_TOPIC = "teamsykefravr.isdialogmelding-behandler-dialogmelding-bestilling"
        private val log = LoggerFactory.getLogger(BehandlerDialogmeldingProducer::class.java)
    }
}
