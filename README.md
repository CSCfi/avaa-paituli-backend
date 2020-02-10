# AVAA Paituli Rahti

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```
./mvnw quarkus:dev
```

## Packaging and running the application

The application is packageable using `./mvnw package`.
It produces the executable `paituli-1.0.0-SNAPSHOT-runner.jar` file in `/target` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/lib` directory.

## Creating a native executable

You can create a native executable using: `./mvnw package -Pnative`.

Or you can use Docker to build the native executable using: `./mvnw package -Pnative -Dquarkus.native.container-build=true`.

You can then execute your binary: `./target/paituli-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/building-native-image-guide 

## Deploying to Rahti Openshift (from local by johannes. probably very different in CI/CD)
```
./mvnw package
oc new-build --strategy docker --dockerfile - --code . --name paituli-test < src/main/docker/Dockerfile.jvm
oc patch bc/paituli-test -p "{\"spec\":{\"strategy\":{\"dockerStrategy\":{\"dockerfilePath\":\"src/main/docker/Dockerfile.native\"}}}}"
oc start-build paituli-test --from-dir=. --follow
```

The DB credentials need to be injected to env variables DB_USERNAME, DB_PASSWORD and DB_CONN_URL ("db4.csc.fi:5521" e.g)

