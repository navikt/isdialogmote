package no.nav.syfo.testhelper

import no.nav.common.KafkaEnvironment
import no.nav.syfo.cronjob.statusendring.DialogmoteStatusEndringProducer.Companion.DIALOGMOTE_STATUS_ENDRING_TOPIC
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BRUKERNOTIFIKASJON_DONE_TOPIC
import no.nav.syfo.brev.arbeidstaker.brukernotifikasjon.BRUKERNOTIFIKASJON_OPPGAVE_TOPIC

fun testKafka(
    autoStart: Boolean = false,
    withSchemaRegistry: Boolean = false,
    topicNames: List<String> = listOf(
        BRUKERNOTIFIKASJON_OPPGAVE_TOPIC,
        BRUKERNOTIFIKASJON_DONE_TOPIC,
        DIALOGMOTE_STATUS_ENDRING_TOPIC,
    )
) = KafkaEnvironment(
    autoStart = autoStart,
    withSchemaRegistry = withSchemaRegistry,
    topicNames = topicNames,
)
