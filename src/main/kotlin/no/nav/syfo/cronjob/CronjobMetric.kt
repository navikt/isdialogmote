package no.nav.syfo.cronjob

import io.prometheus.client.Counter
import no.nav.syfo.metric.METRICS_NS

const val CRONJOB_JOURNALFORING_VARSEL_UPDATE = "cronjob_journalforing_update_count"
const val CRONJOB_JOURNALFORING_VARSEL_FAIL = "cronjob_journalforing_fail_count"
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

const val CRONJOB_PUBLISH_STATUS_ENDRING_UPDATE = "cronjob_publish_status_endring_update_count"
const val CRONJOB_PUBLISH_STATUS_ENDRING_FAIL = "cronjob_publish_status_endring_fail_count"
val COUNT_CRONJOB_PUBLISH_STATUS_ENDRING_UPDATE: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CRONJOB_PUBLISH_STATUS_ENDRING_UPDATE)
    .help("Counts the number of updates in Cronjob - KafkaDialogmoteStatusEndring")
    .register()
val COUNT_CRONJOB_PUBLISH_STATUS_ENDRING_FAIL: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CRONJOB_PUBLISH_STATUS_ENDRING_FAIL)
    .help("Counts the number of failures in Cronjob - KafkaDialogmoteStatusEndring")
    .register()
