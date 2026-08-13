# Kyo

[Kyo](https://getkyo.io/) is an algebraic effect system for Scala. pgmq4s provides `KyoPgmqClient` and `KyoPgmqAdmin` backed by a [kyo-sql](https://getkyo.io/) `SqlClient`.

<div class="admonition warning">
<div class="admonition-title">Warning</div>
<p>Kyo supports <strong>JVM, Scala.js, and Scala Native</strong> and requires <strong>JDK 25</strong> to build. Scala.js database connections require Node.js; browsers cannot open PostgreSQL TCP connections.</p>
</div>

## Dependency

```scala
libraryDependencies ++= Seq(
  "io.github.matejcerny" %% "pgmq4s-core"   % "{{ projectVersion }}",
  "io.github.matejcerny" %% "pgmq4s-kyo"   % "{{ projectVersion }}",
  "io.github.matejcerny" %% "pgmq4s-circe" % "{{ projectVersion }}" // or any JSON codec
)
```

Use `%%%` instead of `%%` in a cross-platform SBT project.

No JDBC driver is needed — `kyo-sql-postgres` talks the Postgres wire protocol directly.

## Effect type

Every operation returns `KyoPgmq[A]`, an alias for Kyo's pending-effect type:

```scala
type KyoPgmq[A] = A < (Async & Abort[Throwable])
```

The error channel is `Throwable` rather than `SqlException` because payload decoding failures and rejected queue names land there too. `Abort.run[SqlException]` still peels out only the SQL failures.

You do **not** need `pgmq4s-cats` — the module ships its own `PgmqEffect[KyoPgmq]` instance.

## Setup

Open an `SqlClient` from a Postgres URL and pass it to the client and admin:

```scala
import _root_.kyo.*
import pgmq4s.*
import pgmq4s.domain.*
import pgmq4s.kyo.{KyoPgmqAdmin, KyoPgmqClient}

val postgresUrl = "postgres://pgmq:pgmq@localhost:5432/pgmq"

SqlClient.initWith(postgresUrl): sqlClient =>
  val client: PgmqClient[KyoPgmq] = KyoPgmqClient(sqlClient)
  val admin: PgmqAdmin[KyoPgmq]   = KyoPgmqAdmin(sqlClient)
  ...
```

`SqlClient.initWith` scopes the connection pool to the block. Use `SqlClient.init` inside a `Scope` if you want to manage the lifetime yourself.

## Full Example

```scala
import _root_.kyo.*
import io.circe.{Decoder, Encoder}
import pgmq4s.*
import pgmq4s.circe.given
import pgmq4s.domain.*
import pgmq4s.kyo.{KyoPgmqAdmin, KyoPgmqClient}

case class OrderCreated(orderId: Long, email: String) derives Encoder.AsObject, Decoder

object KyoExample extends KyoApp:
  private val postgresUrl = "postgres://pgmq:pgmq@localhost:5432/pgmq"
  private val queue = q"orders"

  run {
    SqlClient.initWith(postgresUrl): sqlClient =>
      val client = KyoPgmqClient(sqlClient)
      val admin  = KyoPgmqAdmin(sqlClient)

      for
        _ <- admin.createQueue(queue)
        _ <- client.send(queue, OrderCreated(1L, "dev@example.com"))
        messages <- client.read[OrderCreated](queue, 30.secondsVisibility, 10.messages)
        _ <- Console.printLine(s"read: ${messages.map(_.payload)}")
      yield ()
  }
```
