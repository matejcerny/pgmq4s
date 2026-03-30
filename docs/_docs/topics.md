# Topic-Based Routing

Pub/sub-style message routing via topics and wildcard bindings. Patterns use `*` (match one segment) and `#` (match zero or more segments).

## Bind and Send

Bind a pattern to a queue, then send via routing key:

```scala
admin.bindTopic(tp"orders.*", queue)

// Delivers to all queues bound to patterns matching "orders.created"
val recipientCount: F[Int] =
  client.sendTopic(rk"orders.created", payload)
```

Batch send returns which queues received which message IDs:

```scala
val results: F[List[TopicMessageId]] =
  client.sendBatchTopic(rk"orders.created", payloads)
// Each TopicMessageId contains queueName and msgId
```

## Test Routing

Test which queues would match without sending:

```scala
val matches: F[List[RoutingMatch]] =
  admin.testRouting(rk"orders.eu.created")
// Each RoutingMatch contains pattern, queueName, compiledRegex
```

## Validation

Both `RoutingKey` and `TopicPattern` are validated client-side to match PGMQ's rules. Each type offers three construction methods:

- **String interpolator** (`rk"..."` / `tp"..."`) — compile-time validation
- **`apply`** — runtime validation returning `Either[String, A]`
- **`unsafe`** — skips validation, use when the value is known to be valid

### Routing Key

`RoutingKey` must be non-empty, max 255 characters, only `[a-zA-Z0-9._-]`, no leading/trailing dots, no consecutive dots.

### Topic Pattern

`TopicPattern` follows the same base rules as `RoutingKey` but additionally allows `*` and `#` wildcards. It rejects `**`, `##`, and adjacent wildcards (`*#` or `#*`).
