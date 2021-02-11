package no.nav.syfo.client.moteplanlegger

import io.prometheus.client.Counter
import no.nav.syfo.metric.METRICS_NS

const val CALL_MOTEADMIN_BASE = "call_moteadmin"

const val CALL_MOTEADMIN_MOTE_BASE = "${CALL_MOTEADMIN_BASE}_mote"
const val CALL_MOTEADMIN_BASE_SUCCESS = "${CALL_MOTEADMIN_MOTE_BASE}_success_count"
const val CALL_MOTEADMIN_BASE_FAIL = "${CALL_MOTEADMIN_MOTE_BASE}_fail_count"
val COUNT_CALL_MOTEADMIN_BASE_SUCCESS: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_MOTEADMIN_BASE_SUCCESS)
    .help("Counts the number of successful calls to Syfomoteadmin - PlanlagtMote")
    .register()
val COUNT_CALL_MOTEADMIN_BASE_FAIL: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_MOTEADMIN_BASE_FAIL)
    .help("Counts the number of failed calls to Syfomoteadmin - PlanlagtMote")
    .register()

const val CALL_MOTEADMIN_BEKREFT_BASE = "${CALL_MOTEADMIN_BASE}_bekreft"
const val CALL_MOTEADMIN_BEKREFT_SUCCESS = "${CALL_MOTEADMIN_BEKREFT_BASE}_success_count"
const val CALL_MOTEADMIN_BEKREFT_FAIL = "${CALL_MOTEADMIN_BEKREFT_BASE}_fail_count"
val COUNT_CALL_MOTEADMIN_BEKREFT_SUCCESS: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_MOTEADMIN_BEKREFT_SUCCESS)
    .help("Counts the number of successful calls to Syfomoteadmin - Bekreft PlanlagtMote")
    .register()
val COUNT_CALL_MOTEADMIN_BEKREFT_FAIL: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_MOTEADMIN_BEKREFT_FAIL)
    .help("Counts the number of failed calls to Syfomoteadmin - Bekreft PlanlagtMote")
    .register()
