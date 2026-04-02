# Messages

`Message[+P, +H]` is a sealed trait hierarchy with two orthogonal dimensions:

- **Direction**: `Message.Outbound` (for sending) / `Message.Inbound` (received from queue)
- **Shape**: `Message.IsPlain` (payload only) / `Message.HasHeaders` (payload + headers)

## Outbound messages (sending)

- **`Message.Outbound.Plain[P]`** — payload only
- **`Message.Outbound.WithHeaders[P, H]`** — payload + headers

## Inbound messages (reading)

- **`Message.Inbound.Plain[P]`** — payload only
- **`Message.Inbound.WithHeaders[P, H]`** — payload + headers

Inbound messages expose the following fields:

| Field        | Type                     | Description                                                                |
|--------------|--------------------------|----------------------------------------------------------------------------|
| `id`         | `MessageId`              | PGMQ-assigned message identifier                                           |
| `readCount`  | `Int`                    | Number of times the message has been delivered                             |
| `enqueuedAt` | `OffsetDateTime`         | When the message was inserted                                              |
| `lastReadAt` | `Option[OffsetDateTime]` | When the message was last delivered; `None` if never read                  |
| `visibleAt`  | `OffsetDateTime`         | When the message becomes visible again (end of current visibility timeout) |
| `payload`    | `P`                      | Decoded payload                                                            |

`Message.HasHeaders` (both inbound and outbound) additionally exposes `headers: H`.

## Sending

Wrap your payload in an `Outbound` case class:

```scala
// Plain message
client.send(queue, Message.Outbound.Plain(order))

// With headers
case class OrderHeaders(region: String, priority: Int)
client.send(queue, Message.Outbound.WithHeaders(order, OrderHeaders("eu", 1)))

// Batch send
client.sendBatch(queue, orders.map(Message.Outbound.Plain(_)))
```

## Reading with Headers

```scala
val messages: F[List[Message.Inbound[OrderCreated, OrderHeaders]]] =
  client.read[OrderCreated, OrderHeaders](queue, 30.secondsVisibility, 10.messages)
```

Reading without headers ignores any headers present:

```scala
val messages: F[List[Message.Inbound.Plain[OrderCreated]]] =
  client.read[OrderCreated](queue, 30.secondsVisibility, 10.messages)
```

<div class="admonition warning">
<div class="admonition-title">Warning</div>
<p>Both the payload type <code>P</code> and the headers type <code>H</code> need <code>PgmqEncoder</code>/<code>PgmqDecoder</code> instances in scope. Make sure your JSON codec bridge import covers both types.</p>
</div>
