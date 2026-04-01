# Messages

`Message[P, +H]` is a sealed enum with two cases:

- **`Message.Plain[P]`** — payload only
- **`Message.WithHeaders[P, H]`** — payload + headers

Both cases expose the following fields:

| Field        | Type                     | Description                                                                |
|--------------|--------------------------|----------------------------------------------------------------------------|
| `id`         | `MessageId`              | PGMQ-assigned message identifier                                           |
| `readCount`  | `Int`                    | Number of times the message has been delivered                             |
| `enqueuedAt` | `OffsetDateTime`         | When the message was inserted                                              |
| `lastReadAt` | `Option[OffsetDateTime]` | When the message was last delivered; `None` if never read                  |
| `visibleAt`  | `OffsetDateTime`         | When the message becomes visible again (end of current visibility timeout) |
| `payload`    | `P`                      | Decoded payload                                                            |

`Message.WithHeaders` additionally exposes `headers: H`.

## Sending with Headers

Provide both type parameters to attach typed headers:

```scala
case class OrderHeaders(region: String, priority: Int)

client.send[OrderCreated, OrderHeaders](queue, order, OrderHeaders("eu", 1))
```

## Reading with Headers

```scala
val messages: F[List[Message[OrderCreated, OrderHeaders]]] =
  client.read[OrderCreated, OrderHeaders](queue, 30.secondsVisibility, 10.messages)
```

Reading without headers ignores any headers present:

```scala
val messages: F[List[Message.Plain[OrderCreated]]] =
  client.read[OrderCreated](queue, 30.secondsVisibility, 10.messages)
```

<div class="admonition warning">
<div class="admonition-title">Warning</div>
<p>Both the payload type <code>P</code> and the headers type <code>H</code> need <code>PgmqEncoder</code>/<code>PgmqDecoder</code> instances in scope. Make sure your JSON codec bridge import covers both types.</p>
</div>
