package no.nav.syfo.cronjob

import io.prometheus.client.Counter
import no.nav.syfo.metric.METRICS_NS

const val CRONJOB_BASE = "cronjob"
const val CRONJOB_JOURNALFORING_BASE = "${CRONJOB_BASE}_journalforing"

const val CRONJOB_JOURNALFORING_SUCCESS = "${CRONJOB_JOURNALFORING_BASE}_success_count"
const val CRONJOB_JOURNALFORING_FAIL = "${CRONJOB_JOURNALFORING_BASE}_fail_count"
val COUNT_CRONJOB_JOURNALFORING_SUCCESS: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CRONJOB_JOURNALFORING_SUCCESS)
    .help("Counts the number of successfu results in Cronjob - DialogmoteVarselJournalforing")
    .register()
val COUNT_CRONJOB_JOURNALFORING_FAIL: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CRONJOB_JOURNALFORING_FAIL)
    .help("Counts the number of failures results in Cronjob - DialogmoteVarselJournalforingt")
    .register()
