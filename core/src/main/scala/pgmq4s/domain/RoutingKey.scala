/*
 * Copyright (c) 2026 Matej Cerny
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package pgmq4s

import scala.quoted.*

opaque type RoutingKey = String

/** Routing key for topic-based message delivery (e.g. `"orders.eu.created"`).
  *
  * PGMQ's `validate_routing_key` enforces: non-empty, max 255 characters, only `[a-zA-Z0-9._-]`, no leading/trailing
  * dots, no consecutive dots. Use [[RoutingKey.apply]] for validated construction, [[RoutingKey.unsafe]] when the value
  * is known to be valid, or the `rk"..."` string interpolator for compile-time checked literals:
  * {{{
  *   val key = rk"orders.eu.created"   // validated at compile time, zero runtime cost
  * }}}
  */
object RoutingKey:
  private val allowed = """^[a-zA-Z0-9._-]+$""".r

  private def validate(key: String): Either[String, RoutingKey] = key match
    case k if k.isEmpty                      => Left("RoutingKey must not be empty")
    case k if k.length > 255                 => Left(s"RoutingKey must be at most 255 characters, got ${key.length}")
    case k if allowed.findFirstIn(k).isEmpty => Left("RoutingKey contains invalid characters (allowed: a-zA-Z0-9._-)")
    case k if k.startsWith(".")              => Left("RoutingKey must not start with a dot")
    case k if k.endsWith(".")                => Left("RoutingKey must not end with a dot")
    case k if k.contains("..")               => Left("RoutingKey must not contain consecutive dots")
    case _                                   => Right(key)

  def apply(key: String): Either[String, RoutingKey] = validate(key)

  def unsafe(key: String): RoutingKey =
    val result = validate(key)
    require(result.isRight, result.left.getOrElse(""))
    key

  private[pgmq4s] def trusted(key: String): RoutingKey = key

  extension (routingKey: RoutingKey) def value: String = routingKey

private[pgmq4s] object RoutingKeyMacro:
  def impl(sc: Expr[StringContext], @scala.annotation.unused args: Expr[Seq[Any]])(using Quotes): Expr[RoutingKey] =
    import quotes.reflect.*
    sc match
      case '{ StringContext(${ Varargs(Exprs(parts)) }*) } =>
        if parts.size != 1 then report.errorAndAbort("rk\"...\" does not support interpolation")
        val key = parts.head
        RoutingKey(key) match
          case Right(_)       => '{ RoutingKey.trusted(${ Expr(key) }) }
          case Left(errorMsg) => report.errorAndAbort(errorMsg)
      case _ =>
        report.errorAndAbort("rk\"...\" requires a string literal")

extension (inline sc: StringContext)
  inline def rk(inline args: Any*): RoutingKey = ${ RoutingKeyMacro.impl('sc, 'args) }
