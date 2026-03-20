# <img src="docs/_assets/images/logo.png" alt="pgmq4s" height="40" style="padding-top:16px" /> pgmq4s

![](https://img.shields.io/badge/Scala%203-%23de3423.svg?logo=scala&logoColor=white)
[![Scala.js](https://img.shields.io/badge/scala_native-0.4.17-337ab7?logoColor=white)](https://scala-native.org/)
[![Scala.js](https://www.scala-js.org/assets/badges/scalajs-1.20.0.svg)](https://www.scala-js.org)
[![Latest version](https://maven-badges.sml.io/sonatype-central/io.github.matejcerny/pgmq4s-core_3/badge.svg)](https://repo1.maven.org/maven2/io/github/matejcerny/pgmq4s-core_3)
[![Build Status](https://github.com/matejcerny/pgmq4s/actions/workflows/ci.yml/badge.svg)](https://github.com/matejcerny/pgmq4s/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/matejcerny/pgmq4s/graph/badge.svg?token=IS8K9HLPT1)](https://codecov.io/gh/matejcerny/pgmq4s)

Scala 3 client for [pgmq](https://github.com/pgmq/pgmq) (Postgres Message Queue).

Supports JVM, Scala.js, and Scala Native.

Database backends:
- Anorm
- Doobie
- Skunk
- Slick

JSON codecs
- Circe
- Jsoniter-scala
- Play JSON
- Spray JSON
- uPickle

Need a specific DB backend or JSON bridge? Create an issue [here](https://github.com/matejcerny/pgmq4s/issues/new).

## Architecture

pgmq4s provides two main traits:

- **`PgmqClient[F]`** — message operations (send, read, pop, archive, delete)
- **`PgmqAdmin[F]`** — queue management and observability (create, drop, list, metrics)

Each database backend provides both: e.g. `DoobiePgmqClient` + `DoobiePgmqAdmin`.

## Getting Started

Add to your `build.sbt` (replace `<version>` with the latest version shown in the badge above):

```scala
libraryDependencies ++= Seq(
  "io.github.matejcerny" %% "pgmq4s-core"   % "<version>",
  "io.github.matejcerny" %% "pgmq4s-circe"  % "<version>",  // or pgmq4s-jsoniter, pgmq4s-play-json, pgmq4s-spray-json, pgmq4s-upickle
  "io.github.matejcerny" %% "pgmq4s-doobie" % "<version>"   // or pgmq4s-anorm, pgmq4s-skunk, pgmq4s-slick
)
```

See the [documentation](https://matejcerny.github.io/pgmq4s) for usage examples.
