package no.nav.syfo.cronjob.dialogmotesvar

import no.nav.syfo.dialogmote.api.domain.KDialogmotesvar
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.*

class DialogmotesvarProducer(
    private val kafkaDialogmotesvarProducer: KafkaProducer<String, KDialogmotesvar>,
) {
    fun sendDialogmotesvar(
        kDialogmotesvar: KDialogmotesvar,
        key: UUID,
    ) {
        try {
            kafkaDialogmotesvarProducer.send(
                ProducerRecord(
                    DIALOGMOTESVAR_TOPIC,
                    key.toString(),
                    kDialogmotesvar,
                )
            ).get()
        } catch (e: Exception) {
            log.error("Exception was thrown when attempting to send KDialogmotesvar with id $key: ${e.message}")
            throw e
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmotesvarProducer::class.java)

        const val DIALOGMOTESVAR_TOPIC = "teamsykefravr.dialogmotesvar"
    }
}
