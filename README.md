# Paituli backend repository

This project uses Quarkus, the Supersonic Subatomic Java Framework.
If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

## Development setup

1. Install JDK 17 or newer. Use the bundled `./mvnw` wrapper rather than a system Maven.
   The build targets Java 17 (`maven.compiler.release`), so a newer JDK builds it fine.
2. Create directories `/tmp/paituli_in` and `/tmp/paituli_out`. They are used for download package input and output.
Alternatively you can override them with the environment variables, see Overriding settings below.
3. Set environment variables `DB_USERNAME`, `DB_PASSWORD` and `DB_CONN_URL` for the database connection.
4. Run the application in development mode:
```
./mvnw quarkus:dev
```

If you invoke your own Maven instead of the wrapper, note that `quarkus-maven-plugin` 3.38.3
declares a minimum of Maven 3.9.16. The wrapper currently pins 3.9.7, which still works.

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

## Packaging and running the application

The application is packageable using `./mvnw package`.
It produces a fast-jar in `target/quarkus-app/`, launched with `java -jar target/quarkus-app/quarkus-run.jar`.
Be aware that it’s not an _über-jar_ — the dependencies live alongside it in `target/quarkus-app/lib/`,
so the whole `target/quarkus-app/` directory has to be deployed.

Note that `./mvnw package` runs only the unit tier, not the integration tests.

## Creating a native executable

You can create a native executable using: `./mvnw package -Pnative`.
Or you can use Docker to build the native executable using: `./mvnw package -Pnative -Dquarkus.native.container-build=true`.
You can then execute your binary: `./target/paituli-backend-1.0.2-runner`
If you want to learn more about building native executables, please consult https://quarkus.io/guides/building-native-image-guide 
