package no.nav.syfo.domain

data class EnhetNr(
    val value: String
) {
    private val fourDigits = Regex("^\\d{4}\$")

    init {
        if (!fourDigits.matches(value)) {
            throw IllegalArgumentException("$value is not a valid EnhetNr")
        }
    }
}
