package no.nav.syfo.client.journalpostdistribusjon

import io.micrometer.core.instrument.Counter
import no.nav.syfo.metric.*

const val CALL_JOURNALPOSTDISTRIBUSJON_BASE = "${METRICS_NS}_call_journalpostdistribusjon"

const val CALL_JOURNALPOSTDISTRIBUSJON_SUCCESS = "${CALL_JOURNALPOSTDISTRIBUSJON_BASE}_success_count"
const val CALL_JOURNALPOSTDISTRIBUSJON_FAIL = "${CALL_JOURNALPOSTDISTRIBUSJON_BASE}_fail_count"

val COUNT_CALL_JOURNALPOSTDISTRIBUSJON_SUCCESS: Counter = Counter
    .builder(CALL_JOURNALPOSTDISTRIBUSJON_SUCCESS)
    .description("Counts the number of successful calls to dokdistFordeling - distribuer journalpost")
    .register(METRICS_REGISTRY)
val COUNT_CALL_JOURNALPOSTDISTRIBUSJON_FAIL: Counter = Counter
    .builder(CALL_JOURNALPOSTDISTRIBUSJON_FAIL)
    .description("Counts the number of failed calls to dokdistFordeling - distribuer journalpost")
    .register(METRICS_REGISTRY)
