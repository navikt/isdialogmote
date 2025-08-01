package no.nav.syfo.infrastructure.client.tokendings

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Counter.builder
import no.nav.syfo.metric.METRICS_NS
import no.nav.syfo.metric.METRICS_REGISTRY

const val CALL_TOKENDINGS_BASE = "${METRICS_NS}_call_tokendings"

const val CALL_TOKENDINGS_TOKEN_OBO_BASE = "${CALL_TOKENDINGS_BASE}_token_obo"
const val CALL_TOKENDINGS_TOKEN_OBO_CACHE_HIT = "${CALL_TOKENDINGS_TOKEN_OBO_BASE}_cache_hit_count"
const val CALL_TOKENDINGS_TOKEN_OBO_CACHE_MISS = "${CALL_TOKENDINGS_TOKEN_OBO_BASE}_cache_miss_count"

val COUNT_CALL_TOKENDINGS_TOKEN_OBO_CACHE_HIT: Counter = builder(CALL_TOKENDINGS_TOKEN_OBO_CACHE_HIT)
    .description("Counts the number of cache hits for calls to tokendings - OBO token")
    .register(METRICS_REGISTRY)

val COUNT_CALL_TOKENDINGS_TOKEN_OBO_CACHE_MISS: Counter = builder(CALL_TOKENDINGS_TOKEN_OBO_CACHE_MISS)
    .description("Counts the number of cache misses for calls to tokendings - OBO token")
    .register(METRICS_REGISTRY)
