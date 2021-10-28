package no.nav.syfo.brev.narmesteleder

import io.micrometer.core.instrument.Timer
import no.nav.syfo.metric.*

const val CALL_API_NL_BREV_BASE = "${METRICS_NS}_call_api_nl_brev"
const val CALL_API_NL_BREV_GET = "${CALL_API_NL_BREV_BASE}_get"
const val CALL_API_NL_BREV_PDF = "${CALL_API_NL_BREV_BASE}_pdf"
const val CALL_API_NL_BREV_LES = "${CALL_API_NL_BREV_BASE}_les"

val HISTOGRAM_CALL_API_NL_BREV_GET_TIMER: Timer = Timer
    .builder(CALL_API_NL_BREV_GET)
    .description("Timer for calls to NarmestelederBrevAPI - Get list")
    .register(METRICS_REGISTRY)

val HISTOGRAM_CALL_API_NL_BREV_PDF_TIMER: Timer = Timer
    .builder(CALL_API_NL_BREV_PDF)
    .description("Timer for calls to NarmestelederBrevAPI - Pdf")
    .register(METRICS_REGISTRY)

val HISTOGRAM_CALL_API_NL_BREV_LES_BASE_TIMER: Timer = Timer
    .builder(CALL_API_NL_BREV_LES)
    .description("Timer for calls to NarmestelederBrevAPI - Les")
    .register(METRICS_REGISTRY)
