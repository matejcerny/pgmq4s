# pgmq4s

![](https://img.shields.io/badge/Scala%203-%23de3423.svg?logo=scala&logoColor=white)
[![Scala.js](https://www.scala-js.org/assets/badges/scalajs-1.20.0.svg)](https://www.scala-js.org)
[![Latest version](https://maven-badges.sml.io/sonatype-central/io.github.matejcerny/pgmq4s_3/badge.svg)](https://repo1.maven.org/maven2/io/github/matejcerny/pgmq4s_3)
[![Build Status](https://github.com/matejcerny/pgmq4s/actions/workflows/ci.yml/badge.svg)](https://github.com/matejcerny/pgmq4s/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/matejcerny/pgmq4s/graph/badge.svg?token=IS8K9HLPT1)](https://codecov.io/gh/matejcerny/pgmq4s)

Scala 3 client for [pgmq](https://github.com/tembo-io/pgmq) (Postgres Message Queue).

Supports JVM, Scala.js, and Scala Native. Database backends: Doobie, Skunk. JSON codecs: Circe, Jsoniter-scala.

## Getting Started

Add to your `build.sbt` (replace `<version>` with the latest version shown in the badge above):

```scala
libraryDependencies ++= Seq(
  "io.github.matejcerny" %% "pgmq4s-core"   % "<version>",
  "io.github.matejcerny" %% "pgmq4s-circe"  % "<version>",  // or pgmq4s-jsoniter
  "io.github.matejcerny" %% "pgmq4s-doobie" % "<version>"   // or pgmq4s-skunk
)
```

See the [documentation](https://matejcerny.github.io/pgmq4s) for usage examples.
