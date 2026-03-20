# Contributing to pgmq4s

Thanks for your interest in contributing! This guide covers the basics you need to get started.

## Prerequisites

- JDK 17+
- SBT

## Building

```sh
sbt compile         # compile everything (all modules, all platforms)
sbt coreJVM/compile # compile a single module
```

## Testing

Unit tests use [weaver-cats](https://github.com/typelevel/weaver-cats) with `SimpleIOSuite` and `pureTest` blocks.

```sh
sbt coreJVM/test                 # run tests for a single module
sbt "testOnly *CirceCodecsSuite" # run a single test suite
sbt rootJVM/test                 # run all JVM tests
```

Integration tests require a running Postgres instance with PGMQ installed. You can start one with:

```sh
docker compose up -d
```

## Code Style

### Scala 3 brace-less syntax

All source files use Scala 3 indentation-based (brace-less) syntax. Do **not** use curly braces for control structures, class bodies, or method bodies. `build.sbt` is the only exception (SBT DSL requires braces).

Scalafmt enforces this automatically via `rewrite.scala3.removeOptionalBraces = true`.

### Formatting

```sh
sbt scalafmtAll   # format all sources
sbt scalafmtCheck # check without modifying
```

Key settings: max 120 columns, ASCII-sorted imports, Scala 3 dialect.

## Regenerating CI Workflows

This project uses [sbt-typelevel](https://typelevel.org/sbt-typelevel/) which generates GitHub Actions workflows from `build.sbt`. After any change to `build.sbt` that affects CI configuration (`githubWorkflow*` settings, cross-build matrix, etc.), regenerate the workflows:

```sh
sbt githubWorkflowGenerate
```

Commit the updated `.github/workflows/` files together with your `build.sbt` changes.

## Generating Documentation

```sh
sbt coreJVM/doc
```

## Submitting Changes

1. Fork the repo and create a feature branch from `main`.
2. Make your changes following the conventions above.
3. Run `sbt scalafmtAll` and `sbt compile` before pushing.
4. Open a pull request against `main`.
