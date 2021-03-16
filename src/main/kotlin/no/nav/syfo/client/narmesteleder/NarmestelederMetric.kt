package no.nav.syfo.client.narmesteleder

import io.prometheus.client.Counter
import no.nav.syfo.metric.METRICS_NS

const val CALL_NARMESTELEDER_BASE = "call_narmesteleder"

const val CALL_PERSON_NARMESTE_LEDER_LIST_SUCCESS = "${CALL_NARMESTELEDER_BASE}_success_count"
const val CALL_PERSON_NARMESTE_LEDER_LIST_FAIL = "${CALL_NARMESTELEDER_BASE}_fail_count"
val COUNT_CALL_PERSON_NARMESTE_LEDER_LIST_SUCCESS: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_PERSON_NARMESTE_LEDER_LIST_SUCCESS)
    .help("Counts the number of successful calls to Syfomoteadmin - NarmesteLeder list")
    .register()
val COUNT_CALL_PERSON_NARMESTE_LEDER_LIST_FAIL: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_PERSON_NARMESTE_LEDER_LIST_FAIL)
    .help("Counts the number of failed calls to Modiasyforest - NarmesteLeder list")
    .register()
