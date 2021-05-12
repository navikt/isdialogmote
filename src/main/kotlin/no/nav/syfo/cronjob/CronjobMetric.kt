package no.nav.syfo.cronjob

import io.prometheus.client.Counter
import no.nav.syfo.metric.METRICS_NS

const val CRONJOB_BASE = "cronjob"
const val CRONJOB_JOURNALFORING_BASE = "${CRONJOB_BASE}_journalforing"

const val CRONJOB_JOURNALFORING_VARSEL_UPDATE = "${CRONJOB_JOURNALFORING_BASE}_update_count"
const val CRONJOB_JOURNALFORING_VARSEL_FAIL = "${CRONJOB_JOURNALFORING_BASE}_fail_count"
val COUNT_CRONJOB_JOURNALFORING_VARSEL_UPDATE: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CRONJOB_JOURNALFORING_VARSEL_UPDATE)
    .help("Counts the number of updates in Cronjob - DialogmoteVarselJournalforing")
    .register()
val COUNT_CRONJOB_JOURNALFORING_VARSEL_FAIL: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CRONJOB_JOURNALFORING_VARSEL_FAIL)
    .help("Counts the number of failures in Cronjob - DialogmoteVarselJournalforingt")
    .register()
