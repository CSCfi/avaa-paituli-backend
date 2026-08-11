# Paituli backend repository

This project uses Quarkus, the Supersonic Subatomic Java Framework.
If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

## Development setup

1. Install JDK 17, Maven 3.6.3 or newer
2. Create directories `/tmp/paituli_in` and `/tmp/paituli_out`. They are used for download package input and output.
Alternatively you can override them with the environment variables, see Overriding settings below.
3. Set environment variables `DB_USERNAME`, `DB_PASSWORD` and `DB_CONN_URL` for the database connection.
3. Run the application in development mode:
```
./mvnw quarkus:dev
```

### Overriding settings

All settings in `src/main/resources/application.properties` can be overridden with environment variables. Remove the
profile prefix if there is one, replace `.` with `_` and uppercase all letters.
 
Then run the application with:
```
export PAITULI_DOWNLOAD_OUTPUTPATH=/home/username/temp ; ./mvnw quarkus:dev
```

## Running tests

The suite is split into two tiers, and they run in different Maven phases.

| Tier | Command | Needs Docker |
|---|---|---|
| Unit tests | `./mvnw test` | no |
| Integration | `./mvnw verify` | yes |

Note that `./mvnw test` and `./mvnw package` run **only** the unit tier.
**Use `./mvnw verify` for the full suite.** It requires a running Docker daemon, because
`PostgresTestResource` starts a Postgres container for the integration tier.

To run the integration tier on its own:
```
./mvnw test-compile surefire:test@integration-tests
```

### Docker API version

Testcontainers 1.x bundles a docker-java that falls back to Docker API version 1.32 rather than negotiating with the daemon.
`src/test/resources/docker-java.properties` pins the version to work around this.
If `verify` still fails on the API version, the pin is outside your daemon's supported range.
Check yours with `docker version --format '{{.Server.APIVersion}}'` and adjust.
If `verify`still complains, check that you don't have a `~/.docker-java.properties` overriding the repo's setting. 

## Packaging and running the application

The application is packageable using `./mvnw package`.
It produces the executable `paituli-1.0.0-SNAPSHOT-runner.jar` file in `/target` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/lib` directory.

## Creating a native executable

You can create a native executable using: `./mvnw package -Pnative`.
Or you can use Docker to build the native executable using: `./mvnw package -Pnative -Dquarkus.native.container-build=true`.
You can then execute your binary: `./target/paituli-1.0.0-SNAPSHOT-runner`
If you want to learn more about building native executables, please consult https://quarkus.io/guides/building-native-image-guide 
