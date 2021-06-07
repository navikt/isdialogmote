package no.nav.syfo.cronjob.statusendring

import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

class DialogmoteStatusEndringProducer(
    private val kafkaDialogmoteStatusEndringProducer: KafkaProducer<String, KDialogmoteStatusEndring>,
) {
    fun sendDialogmoteStatusEndring(
        kDialogmoteStatusEndring: KDialogmoteStatusEndring,
    ) {
        try {
            kafkaDialogmoteStatusEndringProducer.send(
                ProducerRecord(
                    DIALOGMOTE_STATUS_ENDRING_TOPIC,
                    kDialogmoteStatusEndring.getDialogmoteUuid(),
                    kDialogmoteStatusEndring,
                )
            ).get()
        } catch (e: Exception) {
            log.error("Exception was thrown when attempting to send KDialogmoteStatusEndring with id {}: ${e.message}", kDialogmoteStatusEndring.getDialogmoteUuid())
            throw e
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DialogmoteStatusEndringProducer::class.java)

        const val DIALOGMOTE_STATUS_ENDRING_TOPIC = "teamsykefravr.isdialogmote-dialogmote-statusendring"
    }
}
