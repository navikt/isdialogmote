package no.nav.syfo.brev.behandler.kafka

import no.nav.syfo.application.ApplicationEnvironmentKafka
import no.nav.syfo.application.ApplicationState
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

const val DIALOGMELDING_TOPIC = "teamsykefravr.dialogmelding"

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.brev.behandler.kafka")

fun blockingApplicationLogicDialogmeldinger(
    applicationState: ApplicationState,
    applicationEnvironmentKafka: ApplicationEnvironmentKafka,
) {
    val consumerProperties = kafkaDialogmeldingConsumerConfig(applicationEnvironmentKafka)
    val kafkaConsumerDialogmelding = KafkaConsumer<String, KafkaDialogmeldingDTO>(consumerProperties)
    kafkaConsumerDialogmelding.subscribe(listOf(DIALOGMELDING_TOPIC))

    while (applicationState.ready) {
        val records = kafkaConsumerDialogmelding.poll(Duration.ofMillis(1000))
        if (records.count() > 0) {
            processDialogmeldingerFromRecords(records)
            kafkaConsumerDialogmelding.commitSync()
        }
    }
}

private fun processDialogmeldingerFromRecords(records: ConsumerRecords<String, KafkaDialogmeldingDTO>) {
    // TODO: Process dialogmeldinger (svar på dialogmøte-innkallinger)
    records.forEach {
        log.info("Received consumer record with key: ${it.key()} value: ${it.value()}")
    }
}
