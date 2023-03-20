package no.nav.syfo.testhelper

import no.nav.common.KafkaEnvironment
import no.nav.syfo.cronjob.statusendring.DialogmoteStatusEndringProducer.Companion.DIALOGMOTE_STATUS_ENDRING_TOPIC
import no.nav.syfo.identhendelse.kafka.IdenthendelseConsumerService.Companion.PDL_AKTOR_TOPIC

fun testKafka(
    autoStart: Boolean = false,
    withSchemaRegistry: Boolean = false,
    topicNames: List<String> = listOf(
        DIALOGMOTE_STATUS_ENDRING_TOPIC,
        PDL_AKTOR_TOPIC,
    )
) = KafkaEnvironment(
    autoStart = autoStart,
    withSchemaRegistry = withSchemaRegistry,
    topicNames = topicNames,
)
