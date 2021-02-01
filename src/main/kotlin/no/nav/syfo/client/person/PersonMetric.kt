package no.nav.syfo.client.person

import io.prometheus.client.Counter
import no.nav.syfo.metric.METRICS_NS

const val CALL_PERSON_BASE = "call_person"
const val CALL_PERSON_ADRESSEBESKYTTELSE_BASE = "${CALL_PERSON_BASE}_adressebeskyttelse"

const val CALL_PERSON_ADRESSEBESKYTTELSE_SUCCESS = "${CALL_PERSON_ADRESSEBESKYTTELSE_BASE}_success_count"
const val CALL_PERSON_ADRESSEBESKYTTELSE_FAIL = "${CALL_PERSON_ADRESSEBESKYTTELSE_BASE}_fail_count"
val COUNT_CALL_PERSON_ADRESSEBESKYTTELSE_SUCCESS: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_PERSON_ADRESSEBESKYTTELSE_SUCCESS)
    .help("Counts the number of successful calls to Syfoperson - Adressebeskyyttelse")
    .register()
val COUNT_CALL_PERSON_ADRESSEBESKYTTELSE_FAIL: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name(CALL_PERSON_ADRESSEBESKYTTELSE_FAIL)
    .help("Counts the number of failed calls to Syfoperson - Adressebeksyttelse")
    .register()
