![Build status](https://github.com/navikt/isdialogmote/workflows/main/badge.svg?branch=master)

# isdialogmote
Isdialogmote is a backend service for handling of DialogmoteInnkallinger. Dialogmoteinnkallinger are handled by SYFO-veiledere in Syfomodiaperson(https://github.com/navikt/syfomodiaperson) in Modia.

## Technologies used
* Docker
* Gradle
* Kotlin
* Kafka
* Ktor
* Postgres

##### Test Libraries:
* Kluent
* Mockk
* Spek

#### Requirements
* JDK 11

### Build
Run `./gradlew clean shadowJar`

### Lint
Run `./gradlew --continue ktlintCheck`

### Test
Run `./gradlew test -i`

### Run Application

#### Create Docker Image
Creating a docker image should be as simple as `docker build -t ispersonoppgave .`

#### Run Docker Image
`docker run --rm -it -p 8080:8080 ispersonoppgave`
