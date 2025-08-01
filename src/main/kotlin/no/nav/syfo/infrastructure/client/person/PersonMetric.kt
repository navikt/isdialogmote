package no.nav.syfo.infrastructure.client.person

import io.micrometer.core.instrument.Counter
import no.nav.syfo.metric.METRICS_NS
import no.nav.syfo.metric.METRICS_REGISTRY

const val CALL_PERSON_BASE = "${METRICS_NS}_call_person"

const val CALL_PERSON_KONTAKTINFORMASJON_BASE = "${CALL_PERSON_BASE}_kontaktinformasjon"
const val CALL_PERSON_KONTAKTINFORMASJON_SUCCESS = "${CALL_PERSON_KONTAKTINFORMASJON_BASE}_success_count"
const val CALL_PERSON_KONTAKTINFORMASJON_FAIL = "${CALL_PERSON_KONTAKTINFORMASJON_BASE}_fail_count"

val COUNT_CALL_PERSON_KONTAKTINFORMASJON_SUCCESS: Counter = Counter
    .builder(CALL_PERSON_KONTAKTINFORMASJON_SUCCESS)
    .description("Counts the number of successful calls to KRR - Kontaktinformasjon")
    .register(METRICS_REGISTRY)
val COUNT_CALL_PERSON_KONTAKTINFORMASJON_FAIL: Counter = Counter
    .builder(CALL_PERSON_KONTAKTINFORMASJON_FAIL)
    .description("Counts the number of failed calls to KRR - Kontaktinformasjon")
    .register(METRICS_REGISTRY)
