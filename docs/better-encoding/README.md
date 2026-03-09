{%
laika.title = Better Encoding
%}

# Better Encoding

Inspired by Noel Welsh's [book](https://scalawithcats.com/dist/scala-with-cats.html#a-better-encoding) and [conference talk](https://www.youtube.com/watch?v=nyMwp7--rY4), this style uses Scala 3 context functions to thread the client implicitly rather than passing it explicitly through every function.

Given a client in implicit scope, `PgmqClient.*` methods resolve it automatically — no need to carry it through every call.
