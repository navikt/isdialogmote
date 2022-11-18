package no.nav.syfo.identhendelse

import io.ktor.server.testing.*
import kotlinx.coroutines.*
import no.nav.syfo.application.cache.RedisStore
import no.nav.syfo.client.azuread.AzureAdV2Client
import no.nav.syfo.client.pdl.PdlClient
import no.nav.syfo.dialogmote.database.createNewDialogmoteWithReferences
import no.nav.syfo.dialogmote.database.getMotedeltakerArbeidstakerByIdent
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.testhelper.ExternalMockEnvironment
import no.nav.syfo.testhelper.UserConstants
import no.nav.syfo.testhelper.dropData
import no.nav.syfo.testhelper.generator.generateKafkaIdenthendelseDTOGenerator
import no.nav.syfo.testhelper.generator.generateNewDialogmote
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.Protocol

object IdenthendelseServiceSpek : Spek({

    describe(IdenthendelseServiceSpek::class.java.simpleName) {

        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.getInstance()
            val database = externalMockEnvironment.database
            val pdlClient = PdlClient(
                azureAdV2Client = AzureAdV2Client(
                    aadAppClient = externalMockEnvironment.environment.aadAppClient,
                    aadAppSecret = externalMockEnvironment.environment.aadAppSecret,
                    aadTokenEndpoint = externalMockEnvironment.environment.aadTokenEndpoint,
                    redisStore = RedisStore(
                        JedisPool(
                            JedisPoolConfig(),
                            externalMockEnvironment.environment.redisHost,
                            externalMockEnvironment.environment.redisPort,
                            Protocol.DEFAULT_TIMEOUT,
                            externalMockEnvironment.environment.redisSecret
                        )
                    ),
                ),
                pdlClientId = externalMockEnvironment.environment.pdlClientId,
                pdlUrl = externalMockEnvironment.environment.pdlUrl,
            )

            val identhendelseService = IdenthendelseService(
                database = database,
                pdlClient = pdlClient,
            )

            afterEachTest {
                database.dropData()
            }

            describe("Happy path") {
                it("Skal oppdatere database når arbeidstaker har fått ny ident") {
                    val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTOGenerator(hasOldPersonident = true)
                    val newIdent = kafkaIdenthendelseDTO.activePersonident
                    val oldIdent = kafkaIdenthendelseDTO.inactivePersonidenter.first()

                    // Populate database with new dialogmote using old ident for arbeidstaker
                    val newDialogmote = generateNewDialogmote(personIdent = oldIdent)
                    database.connection.use { connection ->
                        connection.createNewDialogmoteWithReferences(
                            newDialogmote = newDialogmote,
                            commit = true,
                        )
                    }

                    // Check that arbeidstaker with old/current personident exist in db before update
                    val currentMotedeltakerArbeidstaker = database.getMotedeltakerArbeidstakerByIdent(oldIdent)
                    currentMotedeltakerArbeidstaker.size shouldBeEqualTo 1

                    // Check that arbeidstaker with new personident do not exist in db before update
                    val newMotedeltakerArbeidstaker = database.getMotedeltakerArbeidstakerByIdent(newIdent)
                    newMotedeltakerArbeidstaker.size shouldBeEqualTo 0

                    runBlocking {
                        identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
                    }

                    // Check that arbeidstaker with new personident exist in db after update
                    val updatedMotedeltakerArbeidstaker = database.getMotedeltakerArbeidstakerByIdent(newIdent)
                    updatedMotedeltakerArbeidstaker.size shouldBeEqualTo 1

                    // Check that arbeidstaker with old personident do not exist in db after update
                    val oldMotedeltakerArbeidstaker = database.getMotedeltakerArbeidstakerByIdent(oldIdent)
                    oldMotedeltakerArbeidstaker.size shouldBeEqualTo 0
                }

                it("Skal ikke oppdatere database når arbeidstaker ikke finnes i databasen") {
                    val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTOGenerator(hasOldPersonident = true)
                    val newIdent = kafkaIdenthendelseDTO.activePersonident
                    val oldIdent = PersonIdent("12333378910")

                    // Check that arbeidstaker with old/current personident do not exist in db before update
                    val currentMotedeltakerArbeidstaker = database.getMotedeltakerArbeidstakerByIdent(oldIdent)
                    currentMotedeltakerArbeidstaker.size shouldBeEqualTo 0

                    // Check that arbeidstaker with new personident do not exist in db before update
                    val newMotedeltakerArbeidstaker = database.getMotedeltakerArbeidstakerByIdent(newIdent)
                    newMotedeltakerArbeidstaker.size shouldBeEqualTo 0

                    runBlocking {
                        identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
                    }

                    // Check that arbeidstaker with new personident still do not exist in db after update
                    val updatedMotedeltakerArbeidstaker = database.getMotedeltakerArbeidstakerByIdent(newIdent)
                    updatedMotedeltakerArbeidstaker.size shouldBeEqualTo 0
                }

                it("Skal ikke oppdatere database når arbeidstaker ikke har gamle identer") {
                    val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTOGenerator(hasOldPersonident = false)
                    val newIdent = kafkaIdenthendelseDTO.activePersonident

                    // Check that arbeidstaker with new personident do not exist in db before update
                    val newMotedeltakerArbeidstaker = database.getMotedeltakerArbeidstakerByIdent(newIdent)
                    newMotedeltakerArbeidstaker.size shouldBeEqualTo 0

                    runBlocking {
                        identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
                    }

                    // Check that arbeidstaker with new personident still do not exist in db after update
                    val updatedMotedeltakerArbeidstaker = database.getMotedeltakerArbeidstakerByIdent(newIdent)
                    updatedMotedeltakerArbeidstaker.size shouldBeEqualTo 0
                }
            }

            describe("Unhappy path") {
                it("Skal kaste feil hvis PDL ikke har oppdatert identen") {
                    val kafkaIdenthendelseDTO = generateKafkaIdenthendelseDTOGenerator(
                        personident = UserConstants.ARBEIDSTAKER_TREDJE_FNR,
                        hasOldPersonident = true,
                    )
                    val oldIdent = kafkaIdenthendelseDTO.inactivePersonidenter.first()

                    // Populate database with new dialogmote using old ident for arbeidstaker
                    val newDialogmote = generateNewDialogmote(personIdent = oldIdent)
                    database.connection.use { connection ->
                        connection.createNewDialogmoteWithReferences(
                            newDialogmote = newDialogmote,
                            commit = true,
                        )
                    }

                    // Check that arbeidstaker with old/current personident exist in db before update
                    val currentMotedeltakerArbeidstaker = database.getMotedeltakerArbeidstakerByIdent(oldIdent)
                    currentMotedeltakerArbeidstaker.size shouldBeEqualTo 1

                    runBlocking {
                        assertFailsWith(IllegalStateException::class) {
                            identhendelseService.handleIdenthendelse(kafkaIdenthendelseDTO)
                        }
                    }
                }
            }
        }
    }
})
