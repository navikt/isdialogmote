package no.nav.syfo.metric

import io.prometheus.client.Counter

const val METRICS_NS = "isdialogmote"

const val CALL_TILGANGSKONTROLL_PERSONS_BASE = "call_tilgangskontroll_persons"
const val CALL_TILGANGSKONTROLL_PERSONS_SUCCESS = "${CALL_TILGANGSKONTROLL_PERSONS_BASE}_success_count"
const val CALL_TILGANGSKONTROLL_PERSONS_FAIL = "${CALL_TILGANGSKONTROLL_PERSONS_BASE}_fail_count"
const val CALL_TILGANGSKONTROLL_PERSONS_FORBIDDEN = "${CALL_TILGANGSKONTROLL_PERSONS_BASE}_forbidden_count"
val COUNT_CALL_TILGANGSKONTROLL_PERSONS_SUCCESS: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_TILGANGSKONTROLL_PERSONS_SUCCESS)
    .help("Counts the number of successful calls to syfo-tilgangskontroll - persons")
    .register()
val COUNT_CALL_TILGANGSKONTROLL_PERSONS_FAIL: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_TILGANGSKONTROLL_PERSONS_FAIL)
    .help("Counts the number of failed calls to syfo-tilgangskontroll - persons")
    .register()
val COUNT_CALL_TILGANGSKONTROLL_PERSONS_FORBIDDEN: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_TILGANGSKONTROLL_PERSONS_FORBIDDEN)
    .help("Counts the number of forbidden calls to syfo-tilgangskontroll - persons")
    .register()

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

const val CALL_TILGANGSKONTROLL_ENHET_BASE = "call_tilgangskontroll_enhet"
const val CALL_TILGANGSKONTROLL_ENHET_SUCCESS = "${CALL_TILGANGSKONTROLL_ENHET_BASE}_success_count"
const val CALL_TILGANGSKONTROLL_ENHET_FAIL = "${CALL_TILGANGSKONTROLL_ENHET_BASE}_fail_count"
const val CALL_TILGANGSKONTROLL_ENHET_FORBIDDEN = "${CALL_TILGANGSKONTROLL_ENHET_BASE}_forbidden_count"
val COUNT_CALL_TILGANGSKONTROLL_ENHET_SUCCESS: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_TILGANGSKONTROLL_ENHET_SUCCESS)
    .help("Counts the number of successful calls to syfo-tilgangskontroll - enhet")
    .register()
val COUNT_CALL_TILGANGSKONTROLL_ENHET_FAIL: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_TILGANGSKONTROLL_ENHET_FAIL)
    .help("Counts the number of failed calls to syfo-tilgangskontroll - enhet")
    .register()
val COUNT_CALL_TILGANGSKONTROLL_ENHET_FORBIDDEN: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_TILGANGSKONTROLL_ENHET_FORBIDDEN)
    .help("Counts the number of forbidden calls to syfo-tilgangskontroll - enhet")
    .register()
