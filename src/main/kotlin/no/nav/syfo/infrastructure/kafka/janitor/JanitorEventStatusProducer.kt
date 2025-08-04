package no.nav.syfo.infrastructure.kafka.janitor

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class JanitorEventStatusProducer(
    private val kafkaProducer: KafkaProducer<String, JanitorEventStatusDTO>,
) {
    fun sendEventStatus(
        eventStatus: JanitorEventStatusDTO,
    ) {
        try {
            kafkaProducer.send(
                ProducerRecord(
                    JANITOR_EVENT_STATUS,
                    eventStatus.eventUUID,
                    eventStatus,
                )
            ).get()
        } catch (e: Exception) {
            log.error("Exception was thrown when attempting to send JanitorEventStatusDTO with id {}: ${e.message}", eventStatus.eventUUID)
            throw e
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)

        const val JANITOR_EVENT_STATUS = "teamsykefravr.syfojanitor-status"
    }
}
