package no.nav.syfo.metric

import io.prometheus.client.Counter

const val METRICS_NS = "isdialogmote"

const val CALL_TILGANGSKONTROLL_PERSON_SUCCESS = "call_tilgangskontroll_person_success_count"
const val CALL_TILGANGSKONTROLL_PERSON_FAIL = "call_tilgangskontroll_person_fail_count"
const val CALL_TILGANGSKONTROLL_PERSON_FORBIDDEN = "call_tilgangskontroll_person_forbidden_count"
val COUNT_CALL_TILGANGSKONTROLL_PERSON_SUCCESS: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_TILGANGSKONTROLL_PERSON_SUCCESS)
    .help("Counts the number of successful calls to syfo-tilgangskontroll - person")
    .register()
val COUNT_CALL_TILGANGSKONTROLL_PERSON_FAIL: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_TILGANGSKONTROLL_PERSON_FAIL)
    .help("Counts the number of failed calls to syfo-tilgangskontroll - person")
    .register()
val COUNT_CALL_TILGANGSKONTROLL_PERSON_FORBIDDEN: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_TILGANGSKONTROLL_PERSON_FORBIDDEN)
    .help("Counts the number of forbidden calls to syfo-tilgangskontroll - person")
    .register()
