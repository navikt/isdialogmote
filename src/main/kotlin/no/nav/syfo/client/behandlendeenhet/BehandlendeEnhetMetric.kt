package no.nav.syfo.client.behandlendeenhet

import io.prometheus.client.Counter
import no.nav.syfo.metric.METRICS_NS

const val CALL_BEHANDLENDEENHET_BASE = "call_behandlendeenhet"

const val CALL_BEHANDLENDEENHET_SUCCESS = "${CALL_BEHANDLENDEENHET_BASE}_success_count"
const val CALL_BEHANDLENDEENHET_FAIL = "${CALL_BEHANDLENDEENHET_BASE}_fail_count"
val COUNT_CALL_BEHANDLENDEENHET_SUCCESS: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_BEHANDLENDEENHET_SUCCESS)
    .help("Counts the number of successful calls to Syfobehandlendeenhet")
    .register()
val COUNT_CALL_BEHANDLENDEENHET_FAIL: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_BEHANDLENDEENHET_FAIL)
    .help("Counts the number of failed calls to Syfobehandlendeenhet")
    .register()
