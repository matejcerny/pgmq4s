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

package pgmq4s.domain

opaque type QueueName = String

/** Queue name, wrapping a plain `String`.
  *
  * PGMQ enforces a 48-character limit, lowercases names server-side, and forbids `$`, `;`, `'`, and `--`. Uppercase
  * letters are rejected to prevent client/server mismatches. Use [[QueueName.apply]] for validated construction,
  * [[QueueName.unsafe]] when the value is known to be valid, or the `q"..."` string interpolator for compile-time
  * checked literals:
  * {{{
  *   val name = q"my-queue"   // validated at compile time, zero runtime cost
  * }}}
  */
object QueueName:
  private val forbidden = """[\$;']|--""".r
  private val uppercase = """[A-Z]""".r

  private def validate(name: String): Either[String, QueueName] =
    if name.isEmpty then Left("QueueName must not be empty")
    else if name.length > 48 then Left(s"QueueName must be at most 48 characters, got ${name.length}")
    else if uppercase.findFirstIn(name).isDefined then
      Left("QueueName must be lowercase (PGMQ lowercases names server-side)")
    else
      forbidden.findFirstIn(name) match
        case Some(m) => Left(s"QueueName contains forbidden character or sequence: '$m'")
        case None    => Right(name)

  def apply(name: String): Either[String, QueueName] = validate(name)

  def unsafe(name: String): QueueName =
    val result = validate(name)
    require(result.isRight, result.left.getOrElse(""))
    name

  private[pgmq4s] def trusted(name: String): QueueName = name

  extension (q: QueueName)
    def value: String = q
    private[pgmq4s] def tableName: String = s"pgmq.q_$q"
    private[pgmq4s] def archiveName: String = s"pgmq.a_$q"

private[pgmq4s] object QueueNameMacro:

  import scala.quoted.*

  def impl(sc: Expr[StringContext], @scala.annotation.unused args: Expr[Seq[Any]])(using Quotes): Expr[QueueName] =
    import quotes.reflect.*
    sc match
      case '{ StringContext(${ Varargs(Exprs(parts)) }*) } =>
        if parts.size != 1 then report.errorAndAbort("q\"...\" does not support interpolation")
        val name = parts.head
        QueueName(name) match
          case Right(_)       => '{ QueueName.trusted(${ Expr(name) }) }
          case Left(errorMsg) => report.errorAndAbort(errorMsg)
      case _ =>
        report.errorAndAbort("q\"...\" requires a string literal")

extension (inline sc: StringContext) inline def q(inline args: Any*): QueueName = ${ QueueNameMacro.impl('sc, 'args) }
