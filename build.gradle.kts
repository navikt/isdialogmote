import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "1.0.0"

object Versions {
    const val avroVersion = "1.10.0"
    const val brukernotifikasjonAvroVersion = "1.2021.01.18-11.12-b9c8c40b98d1"
    const val isdialogmoteSchemaVersion = "1.0.3"
    const val confluentVersion = "5.5.0"
    const val flywayVersion = "7.5.2"
    const val hikariVersion = "4.0.1"
    const val jacksonVersion = "2.11.3"
    const val jedisVersion = "3.6.0"
    const val kafkaVersion = "2.7.0"
    const val kafkaEmbeddedVersion = "2.5.0"
    const val ktorVersion = "1.6.0"
    const val jaxbVersion = "2.3.1"
    const val kluentVersion = "1.61"
    const val logbackVersion = "1.2.3"
    const val logstashEncoderVersion = "6.3"
    const val mockkVersion = "1.10.5"
    const val nimbusjosejwtVersion = "7.5.1"
    const val postgresEmbeddedVersion = "0.13.3"
    const val postgresVersion = "42.2.20"
    const val prometheusVersion = "0.9.0"
    const val redisEmbeddedVersion = "0.7.3"
    const val spekVersion = "2.0.15"
    const val mqVersion = "9.2.2.0"
    const val tjenesteSpesifikasjonerGithubVersion = "1.2020.06.11-19.53-1cad83414166"
}

plugins {
    kotlin("jvm") version "1.5.10"
    id("com.github.johnrengelman.shadow") version "6.1.0"
    id("org.jlleitschuh.gradle.ktlint") version "9.4.1"
}

val githubUser: String by project
val githubPassword: String by project
repositories {
    jcenter()
    maven(url = "https://packages.confluent.io/maven/")
    maven(url = "https://jitpack.io")
    maven {
        url = uri("https://maven.pkg.github.com/navikt/isdialogmote-schema")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
    maven {
        url = uri("https://maven.pkg.github.com/navikt/tjenestespesifikasjoner")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation("io.ktor:ktor-auth-jwt:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-apache:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-cio:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-client-jackson:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-jackson:${Versions.ktorVersion}")
    implementation("io.ktor:ktor-server-netty:${Versions.ktorVersion}")

    implementation("ch.qos.logback:logback-classic:${Versions.logbackVersion}")
    implementation("net.logstash.logback:logstash-logback-encoder:${Versions.logstashEncoderVersion}")

    implementation("io.prometheus:simpleclient_hotspot:${Versions.prometheusVersion}")
    implementation("io.prometheus:simpleclient_common:${Versions.prometheusVersion}")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.jacksonVersion}")
    implementation("javax.xml.bind:jaxb-api:${Versions.jaxbVersion}")
    implementation("org.glassfish.jaxb:jaxb-runtime:${Versions.jaxbVersion}")

    // Cache
    implementation("redis.clients:jedis:${Versions.jedisVersion}")
    testImplementation("it.ozimov:embedded-redis:${Versions.redisEmbeddedVersion}")

    // Database
    implementation("org.postgresql:postgresql:${Versions.postgresVersion}")
    implementation("com.zaxxer:HikariCP:${Versions.hikariVersion}")
    implementation("org.flywaydb:flyway-core:${Versions.flywayVersion}")
    testImplementation("com.opentable.components:otj-pg-embedded:${Versions.postgresEmbeddedVersion}")

    // Kafka
    implementation("org.apache.kafka:kafka_2.12:${Versions.kafkaVersion}")
    implementation("io.confluent:kafka-avro-serializer:${Versions.confluentVersion}")
    implementation("io.confluent:kafka-schema-registry:${Versions.confluentVersion}")
    implementation("com.github.navikt:brukernotifikasjon-schemas:${Versions.brukernotifikasjonAvroVersion}")
    implementation("no.nav.syfo.dialogmote.avro:isdialogmote-schema:${Versions.isdialogmoteSchemaVersion}")
    testImplementation("no.nav:kafka-embedded-env:${Versions.kafkaEmbeddedVersion}")

    // MQ
    implementation("com.ibm.mq:com.ibm.mq.allclient:${Versions.mqVersion}")
    implementation("no.nav.tjenestespesifikasjoner:servicemeldingMedKontaktinformasjon-v1-tjenestespesifikasjon:${Versions.tjenesteSpesifikasjonerGithubVersion}")

    testImplementation("com.nimbusds:nimbus-jose-jwt:${Versions.nimbusjosejwtVersion}")
    testImplementation("io.ktor:ktor-server-test-host:${Versions.ktorVersion}")
    testImplementation("io.mockk:mockk:${Versions.mockkVersion}")
    testImplementation("org.amshove.kluent:kluent:${Versions.kluentVersion}")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:${Versions.ktorVersion}")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:${Versions.spekVersion}") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:${Versions.spekVersion}") {
        exclude(group = "org.jetbrains.kotlin")
    }
}

tasks {
    withType<Jar> {
        manifest.attributes["Main-Class"] = "no.nav.syfo.AppKt"
    }

    create("printVersion") {
        doLast {
            println(project.version)
        }
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
    }

    withType<ShadowJar> {
        archiveBaseName.set("app")
        archiveClassifier.set("")
        archiveVersion.set("")
    }

    withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging.showStandardStreams = true
    }
}
