package no.nav.syfo.metric

import io.micrometer.core.instrument.Counter
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry

const val METRICS_NS = "isdialogmote"

const val CALL_TILGANGSKONTROLL_PERSONS_BASE = "${METRICS_NS}_call_tilgangskontroll_persons"
const val CALL_TILGANGSKONTROLL_PERSONS_SUCCESS = "${CALL_TILGANGSKONTROLL_PERSONS_BASE}_success_count"
const val CALL_TILGANGSKONTROLL_PERSONS_FAIL = "${CALL_TILGANGSKONTROLL_PERSONS_BASE}_fail_count"
const val CALL_TILGANGSKONTROLL_PERSONS_FORBIDDEN = "${CALL_TILGANGSKONTROLL_PERSONS_BASE}_forbidden_count"

const val CALL_TILGANGSKONTROLL_PERSON_BASE = "${METRICS_NS}_call_tilgangskontroll_person"
const val CALL_TILGANGSKONTROLL_PERSON_SUCCESS = "${CALL_TILGANGSKONTROLL_PERSON_BASE}_success_count"
const val CALL_TILGANGSKONTROLL_PERSON_FAIL = "${CALL_TILGANGSKONTROLL_PERSON_BASE}_fail_count"
const val CALL_TILGANGSKONTROLL_PERSON_FORBIDDEN = "${CALL_TILGANGSKONTROLL_PERSON_BASE}_forbidden_count"

const val CALL_TILGANGSKONTROLL_ENHET_BASE = "${METRICS_NS}_call_tilgangskontroll_enhet"
const val CALL_TILGANGSKONTROLL_ENHET_SUCCESS = "${CALL_TILGANGSKONTROLL_ENHET_BASE}_success_count"
const val CALL_TILGANGSKONTROLL_ENHET_FAIL = "${CALL_TILGANGSKONTROLL_ENHET_BASE}_fail_count"
const val CALL_TILGANGSKONTROLL_ENHET_FORBIDDEN = "${CALL_TILGANGSKONTROLL_ENHET_BASE}_forbidden_count"

val METRICS_REGISTRY = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

val COUNT_CALL_TILGANGSKONTROLL_PERSONS_SUCCESS: Counter = Counter
    .builder(CALL_TILGANGSKONTROLL_PERSONS_SUCCESS)
    .description("Counts the number of successful calls to syfo-tilgangskontroll - persons")
    .register(METRICS_REGISTRY)
val COUNT_CALL_TILGANGSKONTROLL_PERSONS_FAIL: Counter = Counter.builder(CALL_TILGANGSKONTROLL_PERSONS_FAIL)
    .description("Counts the number of failed calls to syfo-tilgangskontroll - persons")
    .register(METRICS_REGISTRY)
val COUNT_CALL_TILGANGSKONTROLL_PERSONS_FORBIDDEN: Counter = Counter.builder(CALL_TILGANGSKONTROLL_PERSONS_FORBIDDEN)
    .description("Counts the number of forbidden calls to syfo-tilgangskontroll - persons")
    .register(METRICS_REGISTRY)
val COUNT_CALL_TILGANGSKONTROLL_PERSON_SUCCESS: Counter = Counter.builder(CALL_TILGANGSKONTROLL_PERSON_SUCCESS)
    .description("Counts the number of successful calls to syfo-tilgangskontroll - person")
    .register(METRICS_REGISTRY)
val COUNT_CALL_TILGANGSKONTROLL_PERSON_FAIL: Counter = Counter.builder(CALL_TILGANGSKONTROLL_PERSON_FAIL)
    .description("Counts the number of failed calls to syfo-tilgangskontroll - person")
    .register(METRICS_REGISTRY)
val COUNT_CALL_TILGANGSKONTROLL_PERSON_FORBIDDEN: Counter = Counter.builder(CALL_TILGANGSKONTROLL_PERSON_FORBIDDEN)
    .description("Counts the number of forbidden calls to syfo-tilgangskontroll - person")
    .register(METRICS_REGISTRY)
val COUNT_CALL_TILGANGSKONTROLL_ENHET_SUCCESS: Counter = Counter.builder(CALL_TILGANGSKONTROLL_ENHET_SUCCESS)
    .description("Counts the number of successful calls to syfo-tilgangskontroll - enhet")
    .register(METRICS_REGISTRY)
val COUNT_CALL_TILGANGSKONTROLL_ENHET_FAIL: Counter = Counter.builder(CALL_TILGANGSKONTROLL_ENHET_FAIL)
    .description("Counts the number of failed calls to syfo-tilgangskontroll - enhet")
    .register(METRICS_REGISTRY)
val COUNT_CALL_TILGANGSKONTROLL_ENHET_FORBIDDEN: Counter = Counter.builder(CALL_TILGANGSKONTROLL_ENHET_FORBIDDEN)
    .description("Counts the number of forbidden calls to syfo-tilgangskontroll - enhet")
    .register(METRICS_REGISTRY)
