package no.nav.syfo.domain.dialogmote

import no.nav.syfo.api.dto.EndreDialogmoteDTO

enum class MoteEndringType {
    TID_STED,
    BEHANDLER_LAGT_TIL,
}

fun Dialogmote.finnEndringer(endreDialogmote: EndreDialogmoteDTO): Set<MoteEndringType> {
    val latestTidSted = tidStedList.latest()
    val tidStedChanged = latestTidSted == null ||
        latestTidSted.sted != endreDialogmote.sted ||
        latestTidSted.tid != endreDialogmote.tid ||
        latestTidSted.videoLink.orEmpty() != endreDialogmote.videoLink.orEmpty()
    val behandlerAdded = endreDialogmote.behandler?.deltaker != null

    return buildSet {
        if (tidStedChanged) add(MoteEndringType.TID_STED)
        if (behandlerAdded) add(MoteEndringType.BEHANDLER_LAGT_TIL)
    }
}
