package no.nav.syfo.domain.dialogmote

import no.nav.syfo.api.dto.EndreDialogmoteDTO

enum class MoteendringType {
    TID_STED,
    BEHANDLER_LAGT_TIL,
    BEHANDLER_ENDRET,
}

fun Dialogmote.finnEndringer(endreDialogmote: EndreDialogmoteDTO): Set<MoteendringType> {
    val latestTidSted = this.tidStedList.latest()
    val tidStedChanged = latestTidSted == null ||
        latestTidSted.sted != endreDialogmote.sted ||
        latestTidSted.tid != endreDialogmote.tid ||
        latestTidSted.videoLink.orEmpty() != endreDialogmote.videoLink.orEmpty()
    val nyBehandler = endreDialogmote.behandler?.deltaker
    val behandlerAdded = this.behandler == null && nyBehandler != null
    val behandlerChanged = this.behandler != null &&
        nyBehandler != null &&
        this.behandler.behandlerRef != nyBehandler.behandlerRef

    return buildSet {
        if (tidStedChanged) add(MoteendringType.TID_STED)
        if (behandlerAdded) add(MoteendringType.BEHANDLER_LAGT_TIL)
        if (behandlerChanged) add(MoteendringType.BEHANDLER_ENDRET)
    }
}
