![Build status](https://github.com/navikt/isdialogmote/workflows/main/badge.svg?branch=master)

# isdialogmote

Isdialogmote is a backend service for handling DialogmoteInnkallinger. Dialogmoteinnkallinger are created and edited by
SYFO-veiledere in Syfomodiaperson(https://github.com/navikt/syfomodiaperson) in Modia.

Note! isdialogmote persists the virksomhet for a dialogmote, but never the nærmeste leder. This is because the
sykmeldt-nærmeste leder relation can change at any time, including after a dialogmote has been created. This means that
to get for example a nærmeste leder's email, we always have to get the relation and its accompanying information from
the application that holds the most current information: [narmesteleder](https://github.com/navikt/narmesteleder).

## Technologies used

* Docker
* Gradle
* Kotlin
* Kafka
* Ktor
* Postgres
* Redis
* IBM MQ

##### Test Libraries:

* Kluent
* Mockk
* Spek

#### Requirements

* JDK 11

## Download packages from Github Package Registry

Certain packages (tjenestespesifikasjoner) must be downloaded from Github Package Registry, which requires
authentication. The packages can be downloaded via build.gradle:

```
val githubUser: String by project
val githubPassword: String by project
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/navikt/tjenestespesifikasjoner")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}
```

`githubUser` and `githubPassword` are properties that are set in `~/.gradle/gradle.properties`:

```
githubUser=x-access-token
githubPassword=<token>
```

Where `<token>` is a personal access token with scope `read:packages`(and SSO enabled).

The variables can alternatively be configured as environment variables or used in the command lines:

* `ORG_GRADLE_PROJECT_githubUser`
* `ORG_GRADLE_PROJECT_githubPassword`

```
./gradlew -PgithubUser=x-access-token -PgithubPassword=[token]
```

### Build

Run `./gradlew clean shadowJar`

### Lint (Ktlint)

##### Command line

Run checking: `./gradlew --continue ktlintCheck`

Run formatting: `./gradlew ktlintFormat`

##### Git Hooks

Apply checking: `./gradlew addKtlintCheckGitPreCommitHook`

Apply formatting: `./gradlew addKtlintFormatGitPreCommitHook`

### Test

Run `./gradlew test -i`

### Run Application

#### Create Docker Image

Creating a docker image should be as simple as `docker build -t isdialogmote .`

#### Run Docker Image

`docker run --rm -it -p 8080:8080 isdialogmote`

### Cache

This application uses Redis for caching. Redis is deployed automatically on changes to workflow or config on master
branch. For manual deploy, run: `kubectl apply -f .nais/redis-config.yaml`
or `kubectl apply -f .nais/redisexporter.yaml`.

### Kafka

This application produces the following topic(s):

* isdialogmote-dialogmote-statusendring
