package no.nav.syfo.infrastructure.cronjob

interface DialogmoteCronjob {
    suspend fun run()
    val initialDelayMinutes: Long
    val intervalDelayMinutes: Long
}
