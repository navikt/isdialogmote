package no.nav.syfo.metric

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Timer
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry

const val METRICS_NS = "isdialogmote"

const val CALL_TILGANGSKONTROLL_PERSONS_BASE = "${METRICS_NS}_call_tilgangskontroll_persons"
const val CALL_TILGANGSKONTROLL_PERSONS_SUCCESS = "${CALL_TILGANGSKONTROLL_PERSONS_BASE}_success_count"
const val CALL_TILGANGSKONTROLL_PERSONS_FAIL = "${CALL_TILGANGSKONTROLL_PERSONS_BASE}_fail_count"
const val CALL_TILGANGSKONTROLL_PERSONS_FORBIDDEN = "${CALL_TILGANGSKONTROLL_PERSONS_BASE}_forbidden_count"

const val CALL_TILGANGSKONTROLL_ENHET_BASE = "${METRICS_NS}_call_tilgangskontroll_enhet"
const val CALL_TILGANGSKONTROLL_ENHET_SUCCESS = "${CALL_TILGANGSKONTROLL_ENHET_BASE}_success_count"
const val CALL_TILGANGSKONTROLL_ENHET_FAIL = "${CALL_TILGANGSKONTROLL_ENHET_BASE}_fail_count"
const val CALL_TILGANGSKONTROLL_ENHET_FORBIDDEN = "${CALL_TILGANGSKONTROLL_ENHET_BASE}_forbidden_count"

const val CALL_PDL_SUCCESS = "${METRICS_NS}_call_pdl_success_count"
const val CALL_PDL_FAIL = "${METRICS_NS}_call_pdl_fail_count"

const val KAFKA_CONSUMER_PDL_AKTOR_BASE = "${METRICS_NS}_kafka_consumer_pdl_aktor_v2"
const val KAFKA_CONSUMER_PDL_AKTOR_UPDATES = "${KAFKA_CONSUMER_PDL_AKTOR_BASE}_updates"
const val KAFKA_CONSUMER_PDL_AKTOR_TOMBSTONE = "${KAFKA_CONSUMER_PDL_AKTOR_BASE}_tombstone"

val METRICS_REGISTRY = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

val COUNT_CALL_TILGANGSKONTROLL_PERSONS_SUCCESS: Counter = Counter
    .builder(CALL_TILGANGSKONTROLL_PERSONS_SUCCESS)
    .description("Counts the number of successful calls to istilgangskontroll - persons")
    .register(METRICS_REGISTRY)
val COUNT_CALL_TILGANGSKONTROLL_PERSONS_FAIL: Counter = Counter.builder(CALL_TILGANGSKONTROLL_PERSONS_FAIL)
    .description("Counts the number of failed calls to istilgangskontroll - persons")
    .register(METRICS_REGISTRY)
val COUNT_CALL_TILGANGSKONTROLL_PERSONS_FORBIDDEN: Counter = Counter.builder(CALL_TILGANGSKONTROLL_PERSONS_FORBIDDEN)
    .description("Counts the number of forbidden calls to istilgangskontroll - persons")
    .register(METRICS_REGISTRY)
val COUNT_CALL_TILGANGSKONTROLL_ENHET_SUCCESS: Counter = Counter.builder(CALL_TILGANGSKONTROLL_ENHET_SUCCESS)
    .description("Counts the number of successful calls to istilgangskontroll - enhet")
    .register(METRICS_REGISTRY)
val COUNT_CALL_TILGANGSKONTROLL_ENHET_FAIL: Counter = Counter.builder(CALL_TILGANGSKONTROLL_ENHET_FAIL)
    .description("Counts the number of failed calls to istilgangskontroll - enhet")
    .register(METRICS_REGISTRY)
val COUNT_CALL_TILGANGSKONTROLL_ENHET_FORBIDDEN: Counter = Counter.builder(CALL_TILGANGSKONTROLL_ENHET_FORBIDDEN)
    .description("Counts the number of forbidden calls to istilgangskontroll - enhet")
    .register(METRICS_REGISTRY)
val COUNT_CALL_PDL_SUCCESS: Counter = Counter.builder(CALL_PDL_SUCCESS)
    .description("Counts the number of successful calls to pdl")
    .register(METRICS_REGISTRY)
val COUNT_CALL_PDL_FAIL: Counter = Counter.builder(CALL_PDL_FAIL)
    .description("Counts the number of failed calls to pdl")
    .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_PDL_AKTOR_UPDATES: Counter =
    Counter.builder(KAFKA_CONSUMER_PDL_AKTOR_UPDATES)
        .description("Counts the number of updates in database based on identhendelse received from topic - pdl-aktor-v2")
        .register(METRICS_REGISTRY)
val COUNT_KAFKA_CONSUMER_PDL_AKTOR_TOMBSTONE: Counter =
    Counter.builder(KAFKA_CONSUMER_PDL_AKTOR_TOMBSTONE)
        .description("Counts the number of tombstones received from topic - pdl-aktor-v2")
        .register(METRICS_REGISTRY)

// Timers
const val CALL_TILGANGSKONTROLL_ENHET_TIMER = "${CALL_TILGANGSKONTROLL_ENHET_BASE}_timer"
const val CALL_TILGANGSKONTROLL_PERSONS_TIMER = "${CALL_TILGANGSKONTROLL_PERSONS_BASE}_timer"
const val CALL_DIALOGMOTER_ENHET_TIMER = "${METRICS_NS}_call_dialogmoter_enhet"
val HISTOGRAM_CALL_TILGANGSKONTROLL_ENHET_TIMER: Timer = Timer
    .builder(CALL_TILGANGSKONTROLL_ENHET_TIMER)
    .description("Timer for calls to tilgangskontroll enhet")
    .register(METRICS_REGISTRY)
val HISTOGRAM_CALL_TILGANGSKONTROLL_PERSONS_TIMER: Timer = Timer
    .builder(CALL_TILGANGSKONTROLL_PERSONS_TIMER)
    .description("Timer for calls to tilgangskontroll persons")
    .register(METRICS_REGISTRY)
val HISTOGRAM_CALL_DIALOGMOTER_ENHET_TIMER: Timer = Timer
    .builder(CALL_DIALOGMOTER_ENHET_TIMER)
    .description("Timer for calls to get dialogmoter enhet")
    .register(METRICS_REGISTRY)
