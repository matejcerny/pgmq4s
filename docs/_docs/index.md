# Overview

[PGMQ](https://github.com/pgmq/pgmq) is a lightweight message queue built on top of PostgreSQL — no extra infrastructure, just your existing database. Keep It Simple Stupid, [use postgres](https://github.com/Olshansk/postgres_for_everything).

`pgmq4s` (codename: _Dutchie_) is a Scala 3 client for PGMQ, supporting JVM, Scala.js, and Scala Native.

Requires **PGMQ v1.11.0+** (topic-based routing). Core messaging works with v1.10.0.

<div class="admonition info">
<div class="admonition-title">Note</div>
<p>This library is under active development. The API may contain breaking changes between releases.</p>
</div>

## Features

- **Typelevel ecosystem** — built on Cats Effect with tagless final `PgmqClient[F]` and `PgmqAdmin[F]`
- **Multiple backends** — Doobie, Skunk, Anorm, Slick
- **Multiple JSON codecs** — Circe, Jsoniter-scala, uPickle, Play JSON, Spray JSON
- **Cross-platform** — Skunk backend + Circe/Jsoniter/uPickle codecs work on JVM, JS, and Native
