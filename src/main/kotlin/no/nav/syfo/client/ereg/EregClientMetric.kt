package no.nav.syfo.client.ereg

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Counter.builder
import no.nav.syfo.metric.METRICS_NS
import no.nav.syfo.metric.METRICS_REGISTRY

const val CALL_EREG_ORGANISASJON_BASE = "${METRICS_NS}_call_ereg_organisasjon"
const val CALL_EREG_ORGANISASJON_SUCCESS = "${CALL_EREG_ORGANISASJON_BASE}_success_count"
const val CALL_EREG_ORGANISASJON_FAIL = "${CALL_EREG_ORGANISASJON_BASE}_fail_count"
const val CALL_EREG_ORGANISASJON_NOT_FOUND = "${CALL_EREG_ORGANISASJON_BASE}_not_found_count"

val COUNT_CALL_EREG_ORGANISASJON_SUCCESS: Counter = builder(CALL_EREG_ORGANISASJON_SUCCESS)
    .description("Counts the number of successful calls to Ereg - Organisasjon")
    .register(METRICS_REGISTRY)

val COUNT_CALL_EREG_ORGANISASJON_FAIL: Counter = builder(CALL_EREG_ORGANISASJON_FAIL)
    .description("Counts the number of failed calls to Ereg - Organisasjon")
    .register(METRICS_REGISTRY)

val COUNT_CALL_EREG_ORGANISASJON_NOT_FOUND: Counter = builder(CALL_EREG_ORGANISASJON_NOT_FOUND)
    .description("Counts the number of calls failed to find Organisajon in Ereg")
    .register(METRICS_REGISTRY)
