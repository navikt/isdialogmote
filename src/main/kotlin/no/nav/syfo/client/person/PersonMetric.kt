package no.nav.syfo.client.person

import io.prometheus.client.Counter
import io.prometheus.client.Histogram
import no.nav.syfo.metric.METRICS_NS

const val CALL_PERSON_BASE = "call_person"
const val CALL_PERSON_ADRESSEBESKYTTELSE_BASE = "${CALL_PERSON_BASE}_adressebeskyttelse"

const val CALL_PERSON_ADRESSEBESKYTTELSE_SUCCESS = "${CALL_PERSON_ADRESSEBESKYTTELSE_BASE}_success_count"
const val CALL_PERSON_ADRESSEBESKYTTELSE_FAIL = "${CALL_PERSON_ADRESSEBESKYTTELSE_BASE}_fail_count"
const val CALL_PERSON_ADRESSEBESKYTTELSE_TIMER = "${CALL_PERSON_ADRESSEBESKYTTELSE_BASE}_timer"
val COUNT_CALL_PERSON_ADRESSEBESKYTTELSE_SUCCESS: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_PERSON_ADRESSEBESKYTTELSE_SUCCESS)
    .help("Counts the number of successful calls to Syfoperson - Adressebeskyttelse")
    .register()
val COUNT_CALL_PERSON_ADRESSEBESKYTTELSE_FAIL: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_PERSON_ADRESSEBESKYTTELSE_FAIL)
    .help("Counts the number of failed calls to Syfoperson - Adressebeskyttelse")
    .register()
val HISTOGRAM_CALL_PERSON_ADRESSEBESKYTTELSE_TIMER: Histogram = Histogram.build()
    .namespace(METRICS_NS)
    .name(CALL_PERSON_ADRESSEBESKYTTELSE_TIMER)
    .help("Timer for calls to Syfoperson - Adressebeskyttelse")
    .register()

const val CALL_PERSON_KONTAKTINFORMASJON_BASE = "${CALL_PERSON_BASE}_kontaktinformasjon"

const val CALL_PERSON_KONTAKTINFORMASJON_SUCCESS = "${CALL_PERSON_KONTAKTINFORMASJON_BASE}_success_count"
const val CALL_PERSON_KONTAKTINFORMASJON_FAIL = "${CALL_PERSON_KONTAKTINFORMASJON_BASE}_fail_count"
val COUNT_CALL_PERSON_KONTAKTINFORMASJON_SUCCESS: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_PERSON_KONTAKTINFORMASJON_SUCCESS)
    .help("Counts the number of successful calls to Syfoperson - Adressebeskyttelse")
    .register()
val COUNT_CALL_PERSON_KONTAKTINFORMASJON_FAIL: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_PERSON_KONTAKTINFORMASJON_FAIL)
    .help("Counts the number of failed calls to Syfoperson - Adressebeskyttelse")
    .register()

const val CALL_PERSON_OPPFOLGINGSTILFELLE_BASE = "${CALL_PERSON_BASE}_oppfolgingstilfelle"

const val CALL_PERSON_OPPFOLGINGSTILFELLE_SUCCESS = "${CALL_PERSON_OPPFOLGINGSTILFELLE_BASE}_success_count"
const val CALL_PERSON_OPPFOLGINGSTILFELLE_FAIL = "${CALL_PERSON_OPPFOLGINGSTILFELLE_BASE}_fail_count"
val COUNT_CALL_PERSON_OPPFOLGINGSTILFELLE_SUCCESS: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_PERSON_OPPFOLGINGSTILFELLE_SUCCESS)
    .help("Counts the number of successful calls to Syfoperson - Oppfolgingstifelle")
    .register()
val COUNT_CALL_PERSON_OPPFOLGINGSTILFELLE_FAIL: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_PERSON_OPPFOLGINGSTILFELLE_FAIL)
    .help("Counts the number of failed calls to Syfoperson - Adressebeksyttelse")
    .register()
