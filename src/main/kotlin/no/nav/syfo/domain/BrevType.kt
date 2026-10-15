package no.nav.syfo.domain

enum class BrevType {
    AVLYST,
    INNKALT,
    NYTT_TID_STED, // Denne er erstattet med ENDRET, men beholdes for å kunne mappe mot eksisterende data i databasen
    ENDRET,
    REFERAT,
    REFERAT_ENDRET,
}
