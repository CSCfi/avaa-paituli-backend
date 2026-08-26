# CLAUDE.md
This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this service is
Backend for CSC's Paituli geospatial data portal. A Quarkus 3.38.3 / Java 17 REST service with two
responsibilities:
1. Serve a localized (Finnish/English) catalogue of geodata datasets from Postgres.
2. Package files from a mounted filesystem into either a ZIP archive or a plaintext list of FTP/HTTP
   URLs, delivered as an asynchronous job the client polls.

## Commands
```bash
./mvnw quarkus:dev          # dev mode, port 8080, live reload
./mvnw package              # fast-jar -> target/quarkus-app/quarkus-run.jar
./mvnw package -Pnative     # native binary -> target/paituli-backend-1.0.2-runner
./mvnw test                 # unit tests only 
./mvnw verify               # unit + integration tests; requires Docker
```

The integration tier cannot run in this environment (no Docker). Run `./mvnw test` here; `./mvnw verify` is developer-run locally.

Single test class / method:
```bash
./mvnw test -Dtest=DownloadGeneratorTest
./mvnw test -Dtest=DownloadGeneratorTest#shouldCollectAbsolutePaths
```
Targeting a single integration test needs `-DfailIfNoSpecifiedTests=false`, otherwise the default
execution fails on finding no matching unit test.

A green `./mvnw test` prints **four** stack traces — negative-path tests in `DownloadGeneratorTest`
hitting `LOG.error` in `DownloadGenerator.processJob`. Expected; a fifth means something broke.

## Local setup

1. JDK 17+ and Docker (for the Testcontainers Postgres used by integration tests). The build
   targets 17 via `maven.compiler.release`, so a newer JDK compiles it fine. Raising the target to
   21 is gated on GeoServer, which runs on the same host and needs a Java 17 runtime — Quarkus
   3.38 itself requires only 17.
2. Create `/tmp/paituli_in` and `/tmp/paituli_out` — the `%dev` input and output directories.
3. `DB_USERNAME`, `DB_PASSWORD`, `DB_CONN_URL` (host:port; `/paituli` is appended) must be set —
   exported, or in `.env`, which is read automatically by Quarkus dev mode.

You are denied from accessing .env or running any commands that even *mentions* it.
Populating the file is developer's job. If you suspect a credential problem, best you can do is to suggest it. 

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

`POST /download` returns immediately: `DownloadService.createDownloadJob` puts a `DownloadJob` (UUID
id, plus a separate 8-digit random output filename) into a `ConcurrentHashMap` field on the
`@ApplicationScoped` service, then `managedExecutor.runAsync(() -> processJob(job))` and discards the
future. **Job state is in-process only** — it does not survive restart and breaks behind replicas.

State is three mutable `volatile` fields on `DownloadJob`, not an enum: `progress` (double, and
`processing()` is defined as `progress < 1.0`), `error` (empty string means none), and `cancelled`
(set by the REST layer, polled by the zipper iterator). `DownloadResource.serveOutput` reads them in
a fixed precedence — cancelled → error → still processing → output file missing → serve.

### Path resolution and zipping

`collectAbsolutePaths` prefixes each client-supplied `filePath` with the configured `inputPath`. A
`*` switches to wildcard mode: `findMatchingFiles` splits on the **last** `/` and lists that one
directory — no recursion, no wildcards in directory segments. A wildcard matching nothing is fatal;
a plain path that doesn't exist is only logged and skipped.

Output in `outputPath` is cleaned by a process **outside this service**, on a schedule it cannot
consult, so any output may vanish at any time — that is what `serveOutput`'s missing-file branch is
for. ZIP generation runs a 15 GiB uncompressed pre-check, then `FileOperations.zipper`, whose
iterator checks `job.cancelled` in `hasNext()` — the only cancellation mechanism. `copyFileToZip`
re-packs nested `.zip` inputs entry-by-entry as ISO-8859-1 and stores `.jp2` uncompressed.

## Conventions

- Field injection with package-private `@Inject`; `@ApplicationScoped` beans.
- DTOs and Panache entities use public fields, no getters. JSON binding is JSON-B
  (`quarkus-resteasy-jsonb`), not Jackson.
- Logging is JBoss `org.jboss.logging.Logger`. `DatasetService` and `LogService` currently bypass it
  for `printStackTrace`/`System.err` — follow the `Logger` convention in new code.
- Bean Validation lives on `DownloadRequest` (`@NotEmpty`/`@NotNull`) and is triggered by `@Valid`
  on the resource method.
