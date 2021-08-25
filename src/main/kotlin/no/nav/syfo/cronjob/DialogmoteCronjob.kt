package no.nav.syfo.cronjob

interface DialogmoteCronjob {
    suspend fun run()
    val initialDelayMinutes: Long
    val intervalDelayMinutes: Long
}
