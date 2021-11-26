package no.nav.syfo.dialogmelding.kafka

import no.nav.syfo.application.ApplicationState
import no.nav.syfo.dialogmelding.DialogmeldingService
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

class DialogmeldingConsumerService(
    private val kafkaConsumer: KafkaConsumer<String, KafkaDialogmeldingDTO>,
    private val dialogmeldingService: DialogmeldingService,
    private val applicationState: ApplicationState
) {
    fun startConsumer() {
        kafkaConsumer.subscribe(listOf(DIALOGMELDING_TOPIC))
        while (applicationState.ready) {
            val records = kafkaConsumer.poll(Duration.ofSeconds(POLL_DURATION_SECONDS))
            if (records.count() > 0) {
                processDialogmeldingerFromRecords(records)
                kafkaConsumer.commitSync()
            }
        }
    }

    private fun processDialogmeldingerFromRecords(
        records: ConsumerRecords<String, KafkaDialogmeldingDTO>,
    ) {
        records.forEach {
            log.info("Received consumer record with key: ${it.key()}")
            dialogmeldingService.handleDialogmelding(it.value())
        }
    }

    companion object {
        private const val DIALOGMELDING_TOPIC = "teamsykefravr.dialogmelding"
        private const val POLL_DURATION_SECONDS = 1L
        private val log: Logger = LoggerFactory.getLogger(DialogmeldingConsumerService::class.java)
    }
}
