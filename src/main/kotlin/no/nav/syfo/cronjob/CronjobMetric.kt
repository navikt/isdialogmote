package no.nav.syfo.cronjob

import io.micrometer.core.instrument.Counter
import no.nav.syfo.metric.METRICS_NS
import no.nav.syfo.metric.METRICS_REGISTRY

const val CRONJOB_METRICS_BASE = "${METRICS_NS}_cronjob"
const val CRONJOB_JOURNALFORING_VARSEL_UPDATE = "${CRONJOB_METRICS_BASE}_journalforing_update_count"
const val CRONJOB_JOURNALFORING_VARSEL_FAIL = "${CRONJOB_METRICS_BASE}_journalforing_fail_count"
const val CRONJOB_JOURNALPOST_DISTRIBUSJON_UPDATE = "${CRONJOB_METRICS_BASE}_journalpost-distribusjon_update_count"
const val CRONJOB_JOURNALPOST_DISTRIBUSJON_FAIL = "${CRONJOB_METRICS_BASE}_journalpost-distribusjon_fail_count"
const val CRONJOB_PUBLISH_STATUS_ENDRING_UPDATE = "${CRONJOB_METRICS_BASE}_publish_status_endring_update_count"
const val CRONJOB_PUBLISH_STATUS_ENDRING_FAIL = "${CRONJOB_METRICS_BASE}_publish_status_endring_fail_count"

val COUNT_CRONJOB_JOURNALFORING_VARSEL_UPDATE: Counter = Counter
    .builder(CRONJOB_JOURNALFORING_VARSEL_UPDATE)
    .description("Counts the number of updates in Cronjob - DialogmoteVarselJournalforing")
    .register(METRICS_REGISTRY)
val COUNT_CRONJOB_JOURNALFORING_VARSEL_FAIL: Counter = Counter
    .builder(CRONJOB_JOURNALFORING_VARSEL_FAIL)
    .description("Counts the number of failures in Cronjob - DialogmoteVarselJournalforing")
    .register(METRICS_REGISTRY)
val COUNT_CRONJOB_JOURNALPOST_DISTRIBUSJON_UPDATE: Counter = Counter
    .builder(CRONJOB_JOURNALPOST_DISTRIBUSJON_UPDATE)
    .description("Counts the number of updates in Cronjob - DialogmoteJournalpostDistribusjon")
    .register(METRICS_REGISTRY)
val COUNT_CRONJOB_JOURNALPOST_DISTRIBUSJON_FAIL: Counter = Counter
    .builder(CRONJOB_JOURNALPOST_DISTRIBUSJON_FAIL)
    .description("Counts the number of failures in Cronjob - DialogmoteJournalpostDistribusjon")
    .register(METRICS_REGISTRY)
val COUNT_CRONJOB_PUBLISH_STATUS_ENDRING_UPDATE: Counter = Counter
    .builder(CRONJOB_PUBLISH_STATUS_ENDRING_UPDATE)
    .description("Counts the number of updates in Cronjob - KafkaDialogmoteStatusEndring")
    .register(METRICS_REGISTRY)
val COUNT_CRONJOB_PUBLISH_STATUS_ENDRING_FAIL: Counter = Counter
    .builder(CRONJOB_PUBLISH_STATUS_ENDRING_FAIL)
    .description("Counts the number of failures in Cronjob - KafkaDialogmoteStatusEndring")
    .register(METRICS_REGISTRY)
