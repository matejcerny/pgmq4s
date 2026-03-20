# Installation

## Prerequisites

- **PostgreSQL** with the [PGMQ extension](https://github.com/pgmq/pgmq) installed
- **Scala 3** (3.3.x LTS or newer)

## Dependencies

Pick **one database backend** and **one JSON codec**. The backend pulls in `pgmq4s-core` transitively — no need to add it yourself.

### SBT

```scala
libraryDependencies ++= Seq(
  "io.github.matejcerny" %% "pgmq4s-doobie" % "{{ projectVersion }}",  // backend
  "io.github.matejcerny" %% "pgmq4s-circe"  % "{{ projectVersion }}"   // JSON codec
)
```

### Scala CLI

```scala
//> using dep io.github.matejcerny::pgmq4s-doobie:{{ projectVersion }}
//> using dep io.github.matejcerny::pgmq4s-circe:{{ projectVersion }}
```

### Mill

```scala
def ivyDeps = Agg(
  ivy"io.github.matejcerny::pgmq4s-doobie:{{ projectVersion }}",
  ivy"io.github.matejcerny::pgmq4s-circe:{{ projectVersion }}"
)
```

## Scala.js and Scala Native

For cross-platform projects, use `%%%` (SBT) or `:::` (Mill) instead of `%%`/`::` and pick only the modules that support your platform:

```scala
// SBT
libraryDependencies ++= Seq(
  "io.github.matejcerny" %%% "pgmq4s-skunk" % "{{ projectVersion }}",
  "io.github.matejcerny" %%% "pgmq4s-circe" % "{{ projectVersion }}"
)
```

<div class="admonition warning">
<div class="admonition-title">Warning</div>
<p>Play JSON, Spray JSON, Anorm, Doobie, and Slick are <strong>JVM-only</strong>. Only Skunk and the cross-platform codec modules (Circe, Jsoniter, uPickle) are available on JS and Native.</p>
</div>

## Available Artifacts

| Artifact             | Description                      | Platforms       |
|----------------------|----------------------------------|-----------------|
| `pgmq4s-core`        | Core types and algebra           | JVM, JS, Native |
| `pgmq4s-circe`       | Circe JSON codec bridge          | JVM, JS, Native |
| `pgmq4s-jsoniter`    | Jsoniter-scala JSON codec bridge | JVM, JS, Native |
| `pgmq4s-upickle`     | uPickle JSON codec bridge        | JVM, JS, Native |
| `pgmq4s-play-json`   | Play JSON codec bridge           | JVM only        |
| `pgmq4s-spray-json`  | Spray JSON codec bridge          | JVM only        |
| `pgmq4s-skunk`       | Skunk backend                    | JVM, JS, Native |
| `pgmq4s-doobie`      | Doobie backend                   | JVM only        |
| `pgmq4s-anorm`       | Anorm backend                    | JVM only        |
| `pgmq4s-slick`       | Slick backend                    | JVM only        |

