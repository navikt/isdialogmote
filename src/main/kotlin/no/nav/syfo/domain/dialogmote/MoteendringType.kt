package no.nav.syfo.domain.dialogmote

import no.nav.syfo.api.dto.EndreDialogmoteDTO

enum class MoteendringType {
    TID_STED,
    BEHANDLER_LAGT_TIL,
}

fun Dialogmote.finnEndringer(endreDialogmote: EndreDialogmoteDTO): Set<MoteendringType> {
    val latestTidSted = tidStedList.latest()
    val tidStedChanged = latestTidSted == null ||
        latestTidSted.sted != endreDialogmote.sted ||
        latestTidSted.tid != endreDialogmote.tid ||
        latestTidSted.videoLink.orEmpty() != endreDialogmote.videoLink.orEmpty()
    val behandlerAdded = endreDialogmote.behandler?.deltaker != null

    return buildSet {
        if (tidStedChanged) add(MoteendringType.TID_STED)
        if (behandlerAdded) add(MoteendringType.BEHANDLER_LAGT_TIL)
    }
}
