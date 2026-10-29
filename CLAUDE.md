# CLAUDE.md
This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this service is
Backend for CSC's Paituli geospatial data portal. A Quarkus 3.38.3 / Java 21 REST service with two
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

A green `./mvnw test` prints **one** stack trace, from `BuildInfoServiceTest` feeding invalid JSON
to `BuildInfoService`. Expected; a second means something broke. Tests that expect a logged error
collect it with `LogCapture`, which keeps it off the console.

## Local setup

1. JDK 21+ and Docker (for the Testcontainers Postgres used by integration tests). The build
   targets 21 via `maven.compiler.release`, matching the production runtime.
2. Create `/tmp/paituli_in` and `/tmp/paituli_out` — the `%dev` input and output directories.
3. `DB_USERNAME`, `DB_PASSWORD`, `DB_CONN_URL` (host:port; `/paituli` is appended) must be set —
   exported, or in `.env`, which is read automatically by Quarkus dev mode.

You are denied from accessing .env or running any commands that even *mentions* it.
Populating the file is developer's job. If you suspect a credential problem, best you can do is to suggest it. 

## Architecture

```
fi.csc.avaa.paituli
├── rest/          DatasetResource, DownloadResource,     JAX-RS entry points
│               BuildInfoResource
├── service/       DatasetService, DownloadService, LogService, BuildInfoService
├── download/      DownloadGenerator                      orchestration
│   └── io/        FileOperations, FileSizeOperations     filesystem + zip
├── model/         DownloadRequest, DownloadJob, LocalizedDataset, BuildInfo
├── entity/        Dataset, LogEvent                      Panache entities
└── constants/     Constants, DownloadType
```

### Async download job lifecycle

`POST /download` returns immediately: Puts a `DownloadJob` into a `ConcurrentHashMap` field on the
service and does nothing else. Processing starts on the job's first status poll and discards the future.
The executor is built in `DownloadService.init()` with a configrable `maxAsync` set to and an unbounded queue,
so jobs beyond the cap wait for their turn;
Every `DownloadService.getJob` refreshes `job.lastSeen`, so simple status polls from the frontend acts
as a keep-alive. A `@Scheduled` sweep cancels processing jobs not seen within a configrable timeout.

Currently, **Job state is in-process only** — it does not survive restart and breaks behind replicas.
State is mutable fields on `DownloadJob`, which `DownloadResource.serveOutput` reads in a
fixed precedence — cancelled → error → still processing → output file missing → serve.

### Path resolution, zipping and output cleaning

`collectAbsolutePaths` prefixes each client-supplied `filePath` with the configured `inputPath`. A
`*` switches to wildcard mode: `findMatchingFiles` splits on the **last** `/` and lists that one
directory — no recursion, no wildcards in directory segments. A wildcard matching nothing is fatal;
a plain path that doesn't exist is only logged and skipped.

ZIP generation runs a size-limit pre-check, then `FileOperations.zipper`, whose iterator checks
`job.cancelled` in `hasNext()`, and the copy helpers check it per directory, per nested-zip and per
64 KiB chunk, so a cancel takes effect mid-way; an interrupted path
does not count towards `progress`. 

Output in `outputPath` is cleaned by a process **outside this service**, on a schedule it cannot
consult, so output may vanish at any time — that is what `serveOutput`'s missing-file branch is for.

## Conventions

- Field injection with package-private `@Inject`; `@ApplicationScoped` beans.
- DTOs and Panache entities use public fields, no getters. JSON binding is JSON-B
  (`quarkus-resteasy-jsonb`), not Jackson.
- Logging is JBoss `org.jboss.logging.Logger`. `LogService` currently bypasses it for `System.err`
  — follow the `Logger` convention in new code.
- Bean Validation lives on `DownloadRequest` (`@NotEmpty`/`@NotNull`) and is triggered by `@Valid`
  on the resource method.
