package no.nav.syfo.domain

data class Virksomhetsnummer(val value: String) {
    private val nineDigits = Regex("^\\d{9}\$")

    init {
        if (!nineDigits.matches(value)) {
            throw IllegalArgumentException("$value is not a valid Virksomhetsnummer")
        }

        val lastDigit = value[8].digitToInt()
        if (lastDigit != checkDigit(value)) {
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

private val weights = intArrayOf(3, 2, 7, 6, 5, 4, 3, 2)

private fun checkDigit(value: String): Int {
    val sum = (0..7).sumOf { i -> value[i].digitToInt() * weights[i] }
    val remainder = sum % 11
    return if (remainder == 0) 0 else (11 - remainder)
}
