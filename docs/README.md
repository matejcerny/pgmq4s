{%
laika.title = pgmq4s
%}

# pgmq4s

A purely functional, fully typed Scala 3 client for [pgmq](https://github.com/tembo-io/pgmq) (Postgres Message Queue).

Built with modern Scala in mind, `pgmq4s` provides a boilerplate-free API using context functions, while maintaining 100% compatibility with classic tagless final architectures.

## Features

* **Cross-Platform**: Full support for JVM, **Scala.js**, and **Scala Native**.
* **Database Backends**: Doobie, Skunk
* **JSON Codecs**: Circe, Jsoniter-scala

## Getting Started

Add to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "io.github.matejcerny" %% "pgmq4s-core"   % "@VERSION@",
  "io.github.matejcerny" %% "pgmq4s-circe"  % "@VERSION@",  // or pgmq4s-jsoniter
  "io.github.matejcerny" %% "pgmq4s-doobie" % "@VERSION@"   // or pgmq4s-skunk
)
```

All available artifacts:

| Artifact          | Description                           |
|-------------------|---------------------------------------|
| `pgmq4s-core`     | Core types and algebra                |
| `pgmq4s-circe`    | Circe JSON codec bridge               |
| `pgmq4s-jsoniter` | Jsoniter-scala JSON codec bridge      |
| `pgmq4s-doobie`   | Doobie backend (JVM only)             |
| `pgmq4s-skunk`    | Skunk backend (JVM only)              |
