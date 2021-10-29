package no.nav.syfo.client.narmesteleder

import io.micrometer.core.instrument.Counter
import no.nav.syfo.metric.*

const val CALL_NARMESTELEDER_BASE = "${METRICS_NS}_call_narmesteleder"

const val CALL_NARMESTE_LEDER_CACHE_HIT = "${CALL_NARMESTELEDER_BASE}_cache_hit"
const val CALL_NARMESTE_LEDER_CACHE_MISS = "${CALL_NARMESTELEDER_BASE}_cache_miss"
const val CALL_PERSON_NARMESTE_LEDER_CURRENT_SUCCESS = "${CALL_NARMESTELEDER_BASE}_success_count"
const val CALL_PERSON_NARMESTE_LEDER_CURRENT_FAIL = "${CALL_NARMESTELEDER_BASE}_fail_count"

val COUNT_CALL_NARMESTE_LEDER_CACHE_HIT: Counter = Counter
    .builder(CALL_NARMESTE_LEDER_CACHE_HIT)
    .description("Counts the number of cache hits for aktive ansatte")
    .register(METRICS_REGISTRY)
val COUNT_CALL_NARMESTE_LEDER_CACHE_MISS: Counter = Counter
    .builder(CALL_NARMESTE_LEDER_CACHE_MISS)
    .description("Counts the number of cache miss for aktive ansatte")
    .register(METRICS_REGISTRY)
val COUNT_CALL_PERSON_NARMESTE_LEDER_CURRENT_SUCCESS: Counter = Counter
    .builder(CALL_PERSON_NARMESTE_LEDER_CURRENT_SUCCESS)
    .description("Counts the number of successful calls to Narmesteleder - NarmesteLeder current")
    .register(METRICS_REGISTRY)
val COUNT_CALL_PERSON_NARMESTE_LEDER_CURRENT_FAIL: Counter = Counter
    .builder(CALL_PERSON_NARMESTE_LEDER_CURRENT_FAIL)
    .description("Counts the number of failed calls to Narmesteleder - NarmesteLeder current")
    .register(METRICS_REGISTRY)
