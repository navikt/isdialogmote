package no.nav.syfo.janitor.kafka

import kotlinx.coroutines.delay
import no.nav.syfo.application.ApplicationState
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

class JanitorEventConsumer(
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val applicationState: ApplicationState,
    //private val testdataResetService: TestdataResetService,
) {
    suspend fun startConsumer() {
        kafkaConsumer.subscribe(listOf(JANITOR_EVENT_TOPIC))
        log.info("Started consuming $JANITOR_EVENT_TOPIC topic")
        while (applicationState.ready) {
            if (kafkaConsumer.subscription().isEmpty()) {
                kafkaConsumer.subscribe(listOf(JANITOR_EVENT_TOPIC))
            }
            try {
                val records = kafkaConsumer.poll(Duration.ofSeconds(POLL_DURATION_SECONDS))
                if (records.count() > 0) {
                    records.forEach { record ->
                        if (record.value() != null) {
                            //testdataResetService.resetTestdata(PersonIdent(record.value()))
                        } else {
                            log.warn("TestdataResetConsumer: Value of ConsumerRecord from topic $JANITOR_EVENT_TOPIC is null")
                        }
                    }
                    kafkaConsumer.commitSync()
                }
            } catch (ex: Exception) {
                log.warn("Error running kafka consumer for $JANITOR_EVENT_TOPIC, unsubscribing and waiting $DELAY_ON_ERROR_SECONDS seconds for retry", ex)
                kafkaConsumer.unsubscribe()
                delay(DELAY_ON_ERROR_SECONDS.seconds)
            }
        }
    }

    companion object {
        const val JANITOR_EVENT_TOPIC = "teamsykefravr.syfojanitor-event"
        private const val POLL_DURATION_SECONDS = 10L
        private const val DELAY_ON_ERROR_SECONDS = 60L
        private val log: Logger = LoggerFactory.getLogger(JanitorEventConsumer::class.java) }
}
