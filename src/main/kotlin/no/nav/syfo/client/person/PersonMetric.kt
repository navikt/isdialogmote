package no.nav.syfo.client.person

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Timer
import no.nav.syfo.metric.*

const val CALL_PERSON_BASE = "${METRICS_NS}_call_person"
const val CALL_PERSON_ADRESSEBESKYTTELSE_BASE = "${CALL_PERSON_BASE}_adressebeskyttelse"
const val CALL_PERSON_ADRESSEBESKYTTELSE_TIMER = "${CALL_PERSON_ADRESSEBESKYTTELSE_BASE}_timer"

const val CALL_PERSON_KONTAKTINFORMASJON_BASE = "${CALL_PERSON_BASE}_kontaktinformasjon"
const val CALL_PERSON_KONTAKTINFORMASJON_SUCCESS = "${CALL_PERSON_KONTAKTINFORMASJON_BASE}_success_count"
const val CALL_PERSON_KONTAKTINFORMASJON_FAIL = "${CALL_PERSON_KONTAKTINFORMASJON_BASE}_fail_count"

const val CALL_PERSON_OPPFOLGINGSTILFELLE_BASE = "${CALL_PERSON_BASE}_oppfolgingstilfelle"
const val CALL_PERSON_OPPFOLGINGSTILFELLE_SUCCESS = "${CALL_PERSON_OPPFOLGINGSTILFELLE_BASE}_success_count"
const val CALL_PERSON_OPPFOLGINGSTILFELLE_FAIL = "${CALL_PERSON_OPPFOLGINGSTILFELLE_BASE}_fail_count"

val HISTOGRAM_CALL_PERSON_ADRESSEBESKYTTELSE_TIMER: Timer = Timer
    .builder(CALL_PERSON_ADRESSEBESKYTTELSE_TIMER)
    .description("Timer for calls to PDL - Adressebeskyttelse")
    .register(METRICS_REGISTRY)
val COUNT_CALL_PERSON_KONTAKTINFORMASJON_SUCCESS: Counter = Counter
    .builder(CALL_PERSON_KONTAKTINFORMASJON_SUCCESS)
    .description("Counts the number of successful calls to Isproxy - Kontaktinformasjon")
    .register(METRICS_REGISTRY)
val COUNT_CALL_PERSON_KONTAKTINFORMASJON_FAIL: Counter = Counter
    .builder(CALL_PERSON_KONTAKTINFORMASJON_FAIL)
    .description("Counts the number of failed calls to Isproxy - Kontaktinformasjon")
    .register(METRICS_REGISTRY)
val COUNT_CALL_PERSON_OPPFOLGINGSTILFELLE_SUCCESS: Counter = Counter
    .builder(CALL_PERSON_OPPFOLGINGSTILFELLE_SUCCESS)
    .description("Counts the number of successful calls to Isproxy - Oppfolgingstifelle")
    .register(METRICS_REGISTRY)
val COUNT_CALL_PERSON_OPPFOLGINGSTILFELLE_FAIL: Counter = Counter
    .builder(CALL_PERSON_OPPFOLGINGSTILFELLE_FAIL)
    .description("Counts the number of failed calls to Isproxy - Oppfolgingstifelle")
    .register(METRICS_REGISTRY)
