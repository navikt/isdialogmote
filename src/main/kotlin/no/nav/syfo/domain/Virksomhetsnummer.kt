package no.nav.syfo.domain

data class Virksomhetsnummer(val value: String) {
    private val nineDigits = Regex("^\\d{9}\$")

    init {
        if (!nineDigits.matches(value)) {
            throw IllegalArgumentException("$value is not a valid Virksomhetsnummer")
        }
    }

    override fun equals(other: Any?): Boolean {
        return when (other) {
            is Virksomhetsnummer -> value == other.value
            else -> false
        }
    }
}
