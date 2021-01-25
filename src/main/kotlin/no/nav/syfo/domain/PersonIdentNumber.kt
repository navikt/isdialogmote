package no.nav.syfo.domain

data class PersonIdentNumber(val value: String) {
    private val elevenDigits = Regex("^\\d{11}\$")

    init {
        if (!elevenDigits.matches(value)) {
            throw IllegalArgumentException("$value is not a valid PersonIdentNumber")
        }
    }
}
