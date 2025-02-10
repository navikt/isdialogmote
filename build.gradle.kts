import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer

group = "no.nav.syfo"
version = "1.0.0"

val altinnCorrespondenceAgencyExternalVersion = "1.2020.01.20-15.44-063ae9f84815"
val cxfVersion = "3.6.5"
val confluentVersion = "7.8.0"
val flywayVersion = "11.3.0"
val hikariVersion = "6.2.1"
val isdialogmoteSchemaVersion = "1.0.5"
val jacksonDataTypeVersion = "2.18.2"
val jedisVersion = "5.2.0"
val jsonVersion = "20250107"
val jettyVersion = "9.4.57.v20241219"
val joseVersion = "0.9.4"
val kafkaVersion = "7.8.0-ce"
val ktorVersion = "3.0.3"
val kluentVersion = "1.73"
val jaxbApiVersion = "2.3.1"
val jaxbRuntimeVersion = "2.3.6"
val jaxsWsApiVersion = "2.3.1"
val jaxwsToolsVersion = "4.0.3"
val logbackVersion = "1.5.16"
val logstashEncoderVersion = "8.0"
val micrometerRegistryVersion = "1.12.13"
val mockkVersion = "1.13.16"
val nimbusjosejwtVersion = "10.0.1"
val postgresEmbeddedVersion = "2.1.0"
val postgresVersion = "42.7.5"
val redisEmbeddedVersion = "0.7.3"
val spekVersion = "2.0.19"
val tjenesteSpesifikasjonerGithubVersion = "1.2020.06.11-19.53-1cad83414166"

plugins {
    kotlin("jvm") version "2.1.10"
    id("com.gradleup.shadow") version "8.3.5"
    id("org.jlleitschuh.gradle.ktlint") version "11.4.2"
    id("com.github.ben-manes.versions") version "0.52.0"
}

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven(url = "https://jitpack.io")
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

    // JWT
    implementation("com.nimbusds:nimbus-jose-jwt:$nimbusjosejwtVersion")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")
    implementation("org.json:json:$jsonVersion")

    // Metrics and Prometheus
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerRegistryVersion")

    // (De-)serialization
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonDataTypeVersion")
    implementation("javax.xml.bind:jaxb-api:$jaxbApiVersion")
    implementation("org.glassfish.jaxb:jaxb-runtime:$jaxbRuntimeVersion")

    // Cache
    implementation("redis.clients:jedis:$jedisVersion")
    testImplementation("it.ozimov:embedded-redis:$redisEmbeddedVersion")

    // Database
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    testImplementation("io.zonky.test:embedded-postgres:$postgresEmbeddedVersion")

    // Kafka
    val excludeLog4j = fun ExternalModuleDependency.() {
        exclude(group = "log4j")
    }
    implementation("org.apache.kafka:kafka_2.13:$kafkaVersion", excludeLog4j)
    constraints {
        implementation("org.bitbucket.b_c:jose4j") {
            because("org.bitbucket.b_c:jose4j:0.9.3 -> https://ossindex.sonatype.org/vulnerability/CVE-2023-51775")
            version {
                require("$joseVersion")
            }
        }
    }
    implementation("io.confluent:kafka-avro-serializer:$confluentVersion", excludeLog4j)
    constraints {
        implementation("org.apache.commons:commons-compress") {
            because("org.apache.commons:commons-compress:1.22 -> https://www.cve.org/CVERecord?id=CVE-2012-2098")
            version {
                require("1.27.1")
            }
        }
        implementation("com.google.guava:guava") {
            because("com.google.guava:guava:30.1.1-jre -> https://www.cve.org/CVERecord?id=CVE-2020-8908")
            version {
                require("33.4.0-jre")
            }
        }
    }
    implementation("io.confluent:kafka-schema-registry:$confluentVersion", excludeLog4j)
    constraints {
        implementation("org.yaml:snakeyaml") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://advisory.checkmarx.net/advisory/vulnerability/CVE-2022-25857/")
            version {
                require("2.3")
            }
        }
        implementation("org.glassfish:jakarta.el") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://advisory.checkmarx.net/advisory/vulnerability/CVE-2021-28170/")
            version {
                require("3.0.4")
            }
        }
        implementation("com.google.protobuf:protobuf-java") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2022-3510")
            version {
                require("3.25.6")
            }
        }
        implementation("org.apache.zookeeper:zookeeper") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2023-44981")
            version {
                require("3.9.3")
            }
        }
        implementation("org.apache.mina:mina-core") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2024-52046")
            version {
                require("2.2.4")
            }
        }
        implementation("io.github.classgraph:classgraph") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2021-47621")
            version {
                require("4.8.179")
            }
        }
        implementation("org.eclipse.jetty:jetty-server") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2023-36478")
            version {
                require(jettyVersion)
            }
        }
        implementation("org.eclipse.jetty:jetty-xml") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2023-36478")
            version {
                require(jettyVersion)
            }
        }
        implementation("org.eclipse.jetty:jetty-servlets") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2023-36478")
            version {
                require(jettyVersion)
            }
        }
        implementation("org.eclipse.jetty.http2:http2-server") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2023-36478")
            version {
                require(jettyVersion)
            }
        }
    }
    implementation("no.nav.syfo.dialogmote.avro:isdialogmote-schema:$isdialogmoteSchemaVersion")
    constraints {
        implementation("org.apache.avro:avro") {
            because("no.nav.syfo.dialogmote.avro:isdialogmote-schema:$isdialogmoteSchemaVersion -> https://nvd.nist.gov/vuln/detail/CVE-2023-39410")
            version {
                require("1.12.0")
            }
        }
    }

    implementation("no.nav.tjenestespesifikasjoner:servicemeldingMedKontaktinformasjon-v1-tjenestespesifikasjon:$tjenesteSpesifikasjonerGithubVersion")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion") {
        exclude(group = "org.jetbrains.kotlin")
    }

    // Soap
    implementation("no.nav.tjenestespesifikasjoner:altinn-correspondence-agency-external-basic:$altinnCorrespondenceAgencyExternalVersion")
    implementation("org.apache.cxf:cxf-rt-frontend-jaxws:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-features-logging:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-transports-http:$cxfVersion")
    implementation("org.apache.cxf:cxf-rt-ws-security:$cxfVersion")
    constraints {
        implementation("org.apache.santuario:xmlsec") {
            because("org.apache.cxf:cxf-rt-ws-security:$cxfVersion -> https://nvd.nist.gov/vuln/detail/CVE-2023-44483")
            version {
                require("2.3.4")
            }
        }
    }
    implementation("javax.xml.ws:jaxws-api:$jaxsWsApiVersion")
    implementation("com.sun.xml.ws:jaxws-tools:$jaxwsToolsVersion") {
        exclude(group = "com.sun.xml.ws", module = "policy")
    }
}

kotlin {
    jvmToolchain(21)
}

tasks {
    jar {
        manifest.attributes["Main-Class"] = "no.nav.syfo.AppKt"
    }

    shadowJar {
        isZip64 = true
        mergeServiceFiles()
        archiveBaseName.set("app")
        archiveClassifier.set("")
        archiveVersion.set("")
        transform(ServiceFileTransformer::class.java) {
            setPath("META-INF/cxf")
            include("bus-extensions.txt")
        }
    }

    test {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging.showStandardStreams = true
    }
}
