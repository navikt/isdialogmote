package no.nav.syfo.util

import java.time.*

val defaultZoneOffset: ZoneOffset = ZoneOffset.UTC

fun nowUTC(): OffsetDateTime = OffsetDateTime.now(defaultZoneOffset)

fun LocalDateTime.toOffsetDateTimeUTC(): OffsetDateTime =
    this.atZone(ZoneId.of("Europe/Oslo")).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
