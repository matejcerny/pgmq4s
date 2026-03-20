# Overview

[PGMQ](https://github.com/pgmq/pgmq) is a lightweight message queue built on top of PostgreSQL — no extra infrastructure, just your existing database. Keep It Simple Stupid, [use postgres](https://github.com/Olshansk/postgres_for_everything).

`pgmq4s` (codename: _Dutchie_) is a Scala 3 client for PGMQ, supporting JVM, Scala.js, and Scala Native.

## Features

- **Typelevel ecosystem** — built on Cats Effect with tagless final `PgmqClient[F]` and `PgmqAdmin[F]`
- **Multiple backends** — Doobie, Skunk, Anorm, Slick
- **Multiple JSON codecs** — Circe, Jsoniter-scala, uPickle, Play JSON, Spray JSON
- **Typed headers** — attach structured metadata alongside payloads
- **Cross-platform** — Skunk backend + Circe/Jsoniter/uPickle codecs work on JVM, JS, and Native
