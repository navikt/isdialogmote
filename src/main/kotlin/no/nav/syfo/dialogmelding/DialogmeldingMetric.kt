package no.nav.syfo.dialogmelding

import io.micrometer.core.instrument.Counter
import no.nav.syfo.metric.METRICS_NS
import no.nav.syfo.metric.METRICS_REGISTRY

const val DIALOGMELDING_METRICS_BASE = "${METRICS_NS}_dialogmelding_innkalling_dialogmote_svar_behandler"
const val CREATE_INNKALLING_DIALOGMOTE_SVAR_BEHANDLER_SUCCESS = "${DIALOGMELDING_METRICS_BASE}_created_success_count"
const val CREATE_INNKALLING_DIALOGMOTE_SVAR_BEHANDLER_FAIL = "${DIALOGMELDING_METRICS_BASE}_created_fail_count"

val COUNT_CREATE_INNKALLING_DIALOGMOTE_SVAR_BEHANDLER_SUCCESS: Counter = Counter
    .builder(CREATE_INNKALLING_DIALOGMOTE_SVAR_BEHANDLER_SUCCESS)
    .description("Counts the number of innkalling dialogmote svar fra behandler created succesfully")
    .register(METRICS_REGISTRY)
val COUNT_CREATE_INNKALLING_DIALOGMOTE_SVAR_BEHANDLER_FAIL: Counter = Counter
    .builder(CREATE_INNKALLING_DIALOGMOTE_SVAR_BEHANDLER_FAIL)
    .description("Counts the number of failures in creation of innkalling dialogmote svar fra behandler")
    .register(METRICS_REGISTRY)
