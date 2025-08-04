package no.nav.syfo.infrastructure.client.altinn

import io.micrometer.core.instrument.Counter
import no.nav.syfo.metric.METRICS_NS
import no.nav.syfo.metric.METRICS_REGISTRY

const val CALL_ALTINN_BASE = "${METRICS_NS}_call_altinn"

const val CALL_ALTINN_MELDINGSTJENESTE_BASE = "${CALL_ALTINN_BASE}_meldingstjeneste"
const val CALL_ALTINN_MELDINGSTJENESTE_SUCCESS = "${CALL_ALTINN_MELDINGSTJENESTE_BASE}_success_count"
const val CALL_ALTINN_MELDINGSTJENESTE_FAIL = "${CALL_ALTINN_MELDINGSTJENESTE_BASE}_fail_count"

val COUNT_CALL_ALTINN_MELDINGSTJENESTE_SUCCESS: Counter = Counter
    .builder(CALL_ALTINN_MELDINGSTJENESTE_SUCCESS)
    .description("Counts the number of successful calls to Meldingstjeneste - Altinn")
    .register(METRICS_REGISTRY)
val COUNT_CALL_ALTINN_MELDINGSTJENESTE_FAIL: Counter = Counter
    .builder(CALL_ALTINN_MELDINGSTJENESTE_FAIL)
    .description("Counts the number of failed calls to Meldingstjeneste - Altinn")
    .register(METRICS_REGISTRY)
