# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this service is

Backend for CSC's PaITuli geospatial data portal. A Quarkus 3.38.3 / Java 21 REST service with two
responsibilities:

1. Serve a localized (Finnish/English) catalogue of geodata datasets from Postgres.
2. Package files from a mounted filesystem into either a ZIP archive or a plaintext list of FTP/HTTP
   URLs, delivered as an asynchronous job the client polls.

Roughly 1,000 lines of main source across 18 classes. Artifact version is 1.0.2 (`pom.xml`).

## Commands

> `./mvnw test` is verified in this repo (30 tests green). `./mvnw verify` was confirmed green by
> the developer locally on JDK 21, 2026-08-24, after the Quarkus 3.38.3 / Testcontainers 2.x
> upgrade. The packaging commands are read off `pom.xml` and the README, not yet executed.

```bash
./mvnw quarkus:dev          # dev mode, port 8080, live reload
./mvnw package              # fast-jar -> target/quarkus-app/quarkus-run.jar
./mvnw package -Pnative     # native binary -> target/paituli-backend-1.0.2-runner
./mvnw test                 # UNIT TESTS ONLY (see split below)
./mvnw verify               # unit + integration tests; requires Docker
```

Single test class / method:

```bash
./mvnw test -Dtest=DownloadGeneratorTest
./mvnw test -Dtest=DownloadGeneratorTest#shouldCollectAbsolutePaths
```

**The test phase split matters.** `pom.xml` configures surefire twice: the default `test` phase
excludes `@Tag("integration")`, and a second execution bound to the `integration-test` phase runs
*only* that group. So `./mvnw test` runs fewer than half the test classes, and `./mvnw package`
(what the README and both Dockerfiles tell you to run) never executes the integration tier at all.
Use `./mvnw verify` for the full suite.

| Tier | Runs in | Classes |
|---|---|---|
| Unit — plain JUnit 5 + Mockito + AssertJ, no Quarkus boot | `test` | `DownloadResourceTest`, `DownloadGeneratorTest`, `DownloadServiceTest`, `DownloadJobTest`, `LocalizedDatasetTest` |
| Integration — `@QuarkusTest` + RestAssured/Panache against Testcontainers 2.x Postgres | `integration-test` | `DownloadResourceValidationsTest`, `DatasetServiceTest`, `LogServiceTest` |

Targeting a single integration test needs `-DfailIfNoSpecifiedTests=false`, otherwise the default
execution fails on finding no matching unit test.

The integration tier cannot run in this environment (no Docker). Run `./mvnw test` here; `./mvnw verify` is developer-run locally, last confirmed green 2026-08-24.

A green `./mvnw test` prints **four** stack traces — negative-path tests in `DownloadGeneratorTest`
hitting `LOG.error` in `DownloadGenerator.processJob`. Expected; a fifth means something broke.

The `native` profile wires `maven-failsafe-plugin`, but no `*IT.java` class exists, so `-Pnative`
runs zero tests.

## Local setup

1. JDK 21+ and Docker (for the Testcontainers Postgres used by integration tests).
2. Create `/tmp/paituli_in` and `/tmp/paituli_out` — the `%dev` input and output directories.
3. `DB_USERNAME`, `DB_PASSWORD`, `DB_CONN_URL` (host:port; `/paituli` is appended) must be set —
   exported, or in `.env`, which is read automatically by Quarkus dev mode.

You are denied from accessing .env or running any commands that even *mentions* it.
Populating the file is developer's job. If you suspect a credential problem, best you can do is to suggest it. 

Any property in `src/main/resources/application.properties` is overridable by environment variable:
drop the profile prefix, replace `.` with `_`, uppercase. E.g.
`PAITULI_DOWNLOAD_OUTPUTPATH=/home/me/tmp ./mvnw quarkus:dev`.

Production additionally requires `QUARKUS_PORT`, `DOWNLOAD_INPUT_PATH`, `DOWNLOAD_OUTPUT_PATH`,
`DOWNLOAD_OUTPUT_BASE_URL`, `DOWNLOAD_FTP_BASE_URL`, `LOG_SALT`, `MAILER_HOST`, `MAILER_PORT`. None
have `:defaultValue` fallbacks, so a missing one fails startup — including the ones that are dead
(see "Legacy surface" below).

## Architecture

```
fi.csc.avaa.paituli
├── rest/          DatasetResource, DownloadResource      JAX-RS entry points
├── service/       DatasetService, DownloadService, LogService
├── download/      DownloadGenerator                      orchestration
│   └── io/        FileOperations, FileSizeOperations     filesystem + zip
├── model/         DownloadRequest, DownloadJob, LocalizedDataset
├── entity/        Dataset, LogEvent                      Panache entities
└── constants/     Constants, DownloadType
```

### Async download job lifecycle

This is the part that requires reading four files together.

`POST /download` returns immediately; the work happens on a `ManagedExecutor` thread.

1. `DownloadResource.generateDownload` → `DownloadService.createDownloadJob`
2. A `DownloadJob` is created (UUID id, plus a separate 8-digit random output filename) and stored
   in a `ConcurrentHashMap` field on the `@ApplicationScoped` `DownloadService`. **Job state is
   in-process only** — it does not survive restart and does not work behind multiple replicas.
3. `managedExecutor.runAsync(() -> downloadGenerator.processJob(job))`; the returned
   `CompletableFuture` is discarded.
4. `DownloadGenerator.processJob` dispatches on `DownloadType` to `generateZip` or `generateUrlList`,
   then calls `LogService.log`.

**Job state is encoded in three mutable `volatile` fields on `DownloadJob`, not an enum:**

| Field | Meaning |
|---|---|
| `progress` (double 0.0–1.0) | `processing()` is defined as `progress < 1.0`; `1.0` means complete |
| `error` (String) | empty string means no error; set from `err.getMessage()` |
| `cancelled` (boolean) | set directly by the REST layer, polled by the zipper iterator |

`DownloadResource.serveOutput` reads these in a fixed precedence — cancelled → error → still
processing → output file missing → serve — and `jobResponse` builds every response body from
`Map.of("message", "ID", "progress", "error")`.

### Path resolution and zipping

`DownloadGenerator.collectAbsolutePaths` prefixes each client-supplied `filePath` with the
configured `inputPath`. A `*` in the path switches to wildcard mode: `findMatchingFiles` splits on
the **last** `/`, converts the filename segment to a regex via `toRegex`, and lists that one
directory (no recursion, no wildcards in directory segments). A wildcard matching nothing is fatal;
a plain path that doesn't exist is logged and silently skipped.

Generated output in `outputPath` is cleaned periodically by a process **outside this service**, on a
schedule it cannot consult — so any output may vanish at any time. That is what
`DownloadResource.serveOutput`'s missing-file branch exists for.

ZIP generation goes through a 15 GiB uncompressed-size pre-check (`FileSizeOperations.count`), then
`FileOperations.zipper` — a custom `Iterable<ZipProgress>` whose `Iterator` writes one entry per
`next()` and checks `job.cancelled` in `hasNext()`. That iterator is the cancellation mechanism.
Two content rules live in `copyFileToZip`: nested `.zip` inputs are unpacked and re-packed
entry-by-entry (read as ISO-8859-1), and `.jp2` files are stored with `Deflater.NO_COMPRESSION`.

### Database

Hibernate ORM with Panache active-record style, mapped onto a **pre-existing legacy schema**. There
is no Flyway or Liquibase and `quarkus.hibernate-orm.database.generation` is unset (default `none`),
so schema changes must be applied to Postgres by hand — e.g. `stac_id` on `Dataset` (commit
ad3b110) requires a manual `ALTER TABLE`.

- `Dataset` — PK `data_id`. Parallel `_fin`/`_eng` columns for name, org and format.
  **`access = 1` means public**; `DatasetService` filters on it via `Dataset.stream("access", 1)`.
- `LogEvent` — maps to the legacy Finnish table `loki` with Finnish column names
  (`organisaatio`, `aineisto`, `tiedotojenlkm` [sic — misspelling is in the live schema], `paiva`),
  id from sequence `loki_event_id_seq`. `type` is an undocumented integer: 1 = ZIP, 2 = LIST,
  3 = fallback.

### Localization

Not a resource-bundle mechanism. `LocalizedDataset.from(dataset, locale)` picks between the `_fin`
and `_eng` entity columns based on `locale.equals(Constants.LOCALE_FI)`. The locale arrives as a raw
path segment (`/datasets/{locale}`) through `Locale.forLanguageTag`, which is BCP-47 — so `fi` works
but `fi_FI` (underscore) yields `Locale.ROOT` and silently serves English.

## Conventions

- Field injection with package-private `@Inject`; `@ApplicationScoped` beans.
- DTOs and Panache entities use public fields, no getters. JSON binding is JSON-B
  (`quarkus-resteasy-jsonb`), not Jackson.
- Logging is JBoss `org.jboss.logging.Logger`. `DatasetService` and `LogService` currently bypass it
  for `printStackTrace`/`System.err` — follow the `Logger` convention in new code.
- Bean Validation lives on `DownloadRequest` (`@NotEmpty`/`@NotNull`) and is triggered by `@Valid`
  on the resource method.

## Legacy surface

PaITuli v4 replaced "submit a job, get an email with a link" with "submit a job, poll, download".
`EmailService` was deleted (cd5906c) and `email` removed from the request model (89b2db0), but the
supporting machinery was left in place and is entirely unreferenced:

- `quarkus-mailer` dependency and the whole `quarkus.mailer.*` config block
- `src/main/resources/messages_{en,fi}.properties`
- the six `MSG_*`/`ERROR_MSG_*` constants in `Constants.java`
- `LogService.hash()` (private, no callers) and the `paituli.log.salt` config it reads
- `paituli.download.outputBaseUrl` — configured for both profiles, injected nowhere
- `LogEvent.saltedhash` and `LogEvent.organization`, now always NULL

Also unused: `quarkus-jdbc-h2`, `quarkus-test-h2` (tests use Testcontainers Postgres),
test-scope `jackson-databind`, and the `jackson.version` pom property.

Don't treat any of the above as live when tracing behaviour.

## Known-stale documentation

`README.md` was brought up to date alongside the Quarkus 3.38.3 upgrade: fast-jar packaging paths,
the 1.0.2 native binary name, step numbering, `./mvnw verify`, and the `%prod` environment
variables including `QUARKUS_PORT`. Treat it as current.

`src/main/docker/Dockerfile.jvm` does not build. There is no CI or deployment configuration in the
repository.
