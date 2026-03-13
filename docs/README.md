{%
laika.title = pgmq4s
%}

# pgmq4s

Scala 3 client for [pgmq](https://github.com/tembo-io/pgmq) (Postgres Message Queue).

Supports JVM, Scala.js, and Scala Native.

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
| `pgmq4s-skunk`    | Skunk backend (JVM, JS, Native)       |
