{%
laika.title = pgmq4s
%}

# pgmq4s

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
- uPickle

Need a specific DB backend or JSON bridge? Create an issue [here](https://github.com/matejcerny/pgmq4s/issues/new).

## Getting Started

Add to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "io.github.matejcerny" %% "pgmq4s-core"   % "@VERSION@",
  "io.github.matejcerny" %% "pgmq4s-circe"  % "@VERSION@",  // or pgmq4s-jsoniter, pgmq4s-play-json, pgmq4s-upickle
  "io.github.matejcerny" %% "pgmq4s-doobie" % "@VERSION@"   // or pgmq4s-anorm, pgmq4s-skunk, pgmq4s-slick
)
```

All available artifacts:

| Artifact            | Description                       |
|---------------------|-----------------------------------|
| `pgmq4s-core`       | Core types and algebra            |
| `pgmq4s-circe`      | Circe JSON codec bridge           |
| `pgmq4s-jsoniter`   | Jsoniter-scala JSON codec bridge  |
| `pgmq4s-play-json`  | Play JSON codec bridge (JVM only) |
| `pgmq4s-upickle`    | uPickle JSON codec bridge         |
| `pgmq4s-anorm`      | Anorm backend (JVM only)          |
| `pgmq4s-doobie`     | Doobie backend (JVM only)         |
| `pgmq4s-skunk`      | Skunk backend (JVM, JS, Native)   |
| `pgmq4s-slick`      | Slick backend (JVM only)          |
