package no.nav.syfo.client.pdfgen

import io.prometheus.client.Counter
import no.nav.syfo.metric.METRICS_NS

const val CALL_PDFGEN_BASE = "call_isdialogmotepdfgen"

const val CALL_PDFGEN_SUCCESS = "${CALL_PDFGEN_BASE}_success_count"
const val CALL_PDFGEN_FAIL = "${CALL_PDFGEN_BASE}_fail_count"
val COUNT_CALL_PDFGEN_SUCCESS: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_PDFGEN_SUCCESS)
    .help("Counts the number of successful calls to Isdialogmotepdfgen")
    .register()
val COUNT_CALL_PDFGEN_FAIL: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_PDFGEN_FAIL)
    .help("Counts the number of failed calls to Syfomoteadmin")
    .register()
