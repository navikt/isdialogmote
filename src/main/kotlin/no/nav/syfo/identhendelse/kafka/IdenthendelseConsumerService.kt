package no.nav.syfo.identhendelse.kafka

import kotlinx.coroutines.delay
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.identhendelse.IdenthendelseService
import no.nav.syfo.metric.COUNT_KAFKA_CONSUMER_PDL_AKTOR_TOMBSTONE
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

class IdenthendelseConsumerService(
    private val kafkaConsumer: KafkaConsumer<String, GenericRecord>,
    private val applicationState: ApplicationState,
    private val identhendelseService: IdenthendelseService,
) {
    suspend fun startConsumer() {
        kafkaConsumer.subscribe(listOf(PDL_AKTOR_TOPIC))
        log.info("Started consuming pdl-aktor topic")
        while (applicationState.ready) {
            if (kafkaConsumer.subscription().isEmpty()) {
                kafkaConsumer.subscribe(listOf(PDL_AKTOR_TOPIC))
            }
            try {
                val records = kafkaConsumer.poll(Duration.ofSeconds(POLL_DURATION_SECONDS))
                if (records.count() > 0) {
                    records.forEach { record ->
                        if (record.value() != null) {
                            identhendelseService.handleIdenthendelse(record.value().toKafkaIdenthendelseDTO())
                        } else {
                            log.warn("Identhendelse: Value of ConsumerRecord from topic $PDL_AKTOR_TOPIC is null, probably due to a tombstone. Contact the owner of the topic if an error is suspected")
                            COUNT_KAFKA_CONSUMER_PDL_AKTOR_TOMBSTONE.increment()
                        }
                    }
                    kafkaConsumer.commitSync()
                }
            } catch (ex: Exception) {
                log.error("Error running kafka consumer for pdl-aktor, unsubscribing and waiting $DELAY_ON_ERROR_SECONDS seconds for retry", ex)
                kafkaConsumer.unsubscribe()
                delay(DELAY_ON_ERROR_SECONDS.seconds)
            }
        }
    }

    companion object {
        const val PDL_AKTOR_TOPIC = "pdl.aktor-v2"
        private const val POLL_DURATION_SECONDS = 10L
        private const val DELAY_ON_ERROR_SECONDS = 60L
        private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.application.identhendelse")
    }
}

fun GenericRecord.toKafkaIdenthendelseDTO(): KafkaIdenthendelseDTO {
    val identifikatorer = (get("identifikatorer") as GenericData.Array<GenericRecord>).map {
        Identifikator(
            idnummer = it.get("idnummer").toString(),
            gjeldende = it.get("gjeldende").toString().toBoolean(),
            type = when (it.get("type").toString()) {
                "FOLKEREGISTERIDENT" -> IdentType.FOLKEREGISTERIDENT
                "AKTORID" -> IdentType.AKTORID
                "NPID" -> IdentType.NPID
                else -> throw IllegalStateException("Har mottatt ident med ukjent type")
            }
        )
    }
    return KafkaIdenthendelseDTO(identifikatorer)
}
