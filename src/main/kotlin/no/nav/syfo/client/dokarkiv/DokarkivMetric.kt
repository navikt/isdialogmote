package no.nav.syfo.client.dokarkiv

import io.prometheus.client.Counter
import no.nav.syfo.metric.METRICS_NS

const val CALL_DOKARKIV_BASE = "call_dokarkiv"
const val CALL_DOKARKIV_JOURNALPOST_BASE = "${CALL_DOKARKIV_BASE}_journalpost"

const val CALL_DOKARKIV_JOURNALPOST_SUCCESS = "${CALL_DOKARKIV_JOURNALPOST_BASE}_success_count"
const val CALL_DOKARKIV_JOURNALPOST_FAIL = "${CALL_DOKARKIV_BASE}_fail_count"
val COUNT_CALL_DOKARKIV_JOURNALPOST_SUCCESS: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_DOKARKIV_JOURNALPOST_SUCCESS)
    .help("Counts the number of successful calls to Dokarkiv - Journalpost")
    .register()
val COUNT_CALL_DOKARKIV_JOURNALPOST_FAIL: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_DOKARKIV_JOURNALPOST_FAIL)
    .help("Counts the number of failed calls to Dokarkiv - Journalpost")
    .register()
