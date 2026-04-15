# Quick Example

This page walks you through a minimal working example.

## Start Postgres with PGMQ

The quickest way to get a [PGMQ](https://github.com/pgmq/pgmq)-enabled Postgres running locally is with Docker:

```yaml
# docker-compose.yml
services:
  postgres:
    image: ghcr.io/pgmq/pg18-pgmq:v1.11.0
    ports:
      - "5432:5432"
    environment:
      POSTGRES_USER: pgmq
      POSTGRES_PASSWORD: pgmq
      POSTGRES_DB: pgmq
```

```bash
docker compose up -d
docker compose exec -T postgres psql -U pgmq -d pgmq -c "CREATE EXTENSION IF NOT EXISTS pgmq;"
```

## Runnable Example with Scala CLI

Save the following as `pgmq-example.scala` and run it with `scala-cli run pgmq-example.scala`:

```scala
//> using dep io.github.matejcerny::pgmq4s-skunk:{{ projectVersion }}
//> using dep io.github.matejcerny::pgmq4s-circe:{{ projectVersion }}

import _root_.skunk.Session
import cats.effect.{IO, IOApp}
import io.circe.{Decoder, Encoder}
import org.typelevel.otel4s.metrics.Meter.Implicits.noop
import org.typelevel.otel4s.trace.Tracer.Implicits.noop
import pgmq4s.*
import pgmq4s.circe.given
import pgmq4s.skunk.{SkunkPgmqAdmin, SkunkPgmqClient}
import scala.concurrent.duration.*

case class OrderCreated(orderId: Long, email: String) derives Encoder.AsObject, Decoder

object Main extends IOApp.Simple:
  val run: IO[Unit] =
    Session
      .Builder[IO]
      .withHost("localhost")
      .withPort(5432)
      .withUserAndPassword("pgmq", "pgmq")
      .withDatabase("pgmq")
      .pooled(10)
      .use: pool =>
        val client = SkunkPgmqClient[IO](pool)
        val admin  = SkunkPgmqAdmin[IO](pool)
        val queue  = q"getting_started"

        for
          _        <- admin.createQueue(queue)
          msgId    <- client.send(queue, OrderCreated(1L, "dev@example.com"))
          messages <- client.read[OrderCreated](queue, 30.secondsVisibility, 10.messages)
          _        <- IO.println(s"Received: ${messages.map(_.payload)}")
        yield ()
```

## Core Concepts

pgmq4s provides two main traits:

- **`PgmqClient[F]`** — message operations: `send`, `read`, `pop`, `archive`, `delete`, `setVisibilityTimeout`
- **`PgmqAdmin[F]`** — queue management: `createQueue`, `dropQueue`, `purgeQueue`, `metrics`, `listQueues`

Each database backend provides implementations of both. Pick the backend that matches your stack.
