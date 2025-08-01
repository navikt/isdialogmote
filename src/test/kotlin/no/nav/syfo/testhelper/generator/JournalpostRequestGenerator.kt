package no.nav.syfo.testhelper.generator

import no.nav.syfo.infrastructure.client.dokarkiv.domain.AvsenderMottaker
import no.nav.syfo.infrastructure.client.dokarkiv.domain.BrevkodeType
import no.nav.syfo.infrastructure.client.dokarkiv.domain.Bruker
import no.nav.syfo.infrastructure.client.dokarkiv.domain.BrukerIdType
import no.nav.syfo.infrastructure.client.dokarkiv.domain.Dokument
import no.nav.syfo.infrastructure.client.dokarkiv.domain.Dokumentvariant
import no.nav.syfo.infrastructure.client.dokarkiv.domain.FiltypeType
import no.nav.syfo.infrastructure.client.dokarkiv.domain.JournalpostKanal
import no.nav.syfo.infrastructure.client.dokarkiv.domain.JournalpostRequest
import no.nav.syfo.infrastructure.client.dokarkiv.domain.JournalpostTema
import no.nav.syfo.infrastructure.client.dokarkiv.domain.JournalpostType
import no.nav.syfo.infrastructure.client.dokarkiv.domain.Sak
import no.nav.syfo.infrastructure.client.dokarkiv.domain.SaksType
import no.nav.syfo.infrastructure.client.dokarkiv.domain.VariantformatType
import no.nav.syfo.testhelper.UserConstants
import java.util.*

fun generateJournalpostRequest(
    tittel: String,
    brevkodeType: BrevkodeType,
    pdf: ByteArray,
    kanal: String,
    varselId: UUID,
) = JournalpostRequest.create(
    avsenderMottaker = AvsenderMottaker.create(
        id = UserConstants.ARBEIDSTAKER_FNR.value,
        idType = BrukerIdType.PERSON_IDENT,
        navn = UserConstants.ARBEIDSTAKERNAVN,
    ),
    bruker = Bruker.create(
        id = UserConstants.ARBEIDSTAKER_FNR.value,
        idType = BrukerIdType.PERSON_IDENT
    ),
    tittel = tittel,
    dokumenter = listOf(
        Dokument.create(
            brevkode = brevkodeType,
            tittel = tittel,
            dokumentvarianter = listOf(
                Dokumentvariant.create(
                    filnavn = tittel,
                    filtype = FiltypeType.PDFA,
                    fysiskDokument = pdf,
                    variantformat = VariantformatType.ARKIV,
                )
            ),
        )
    ),
    kanal = JournalpostKanal.SENTRAL_UTSKRIFT,
    journalfoerendeEnhet = null,
    journalpostType = JournalpostType.UTGAAENDE,
    tema = JournalpostTema.OPPFOLGING,
    sak = Sak.invoke(SaksType.GENERELL),
    eksternReferanseId = varselId.toString(),
)
