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

opaque type TopicPattern = String

/** Binding pattern with `*` (single segment) and `#` (zero or more) wildcards.
  *
  * PGMQ's `validate_topic_pattern` enforces: non-empty, max 255 characters, only `[a-zA-Z0-9._-*#]`, no
  * leading/trailing dots, no consecutive dots, no `**`, no `##`, no adjacent wildcards (`*#` or `#*`). Use
  * [[TopicPattern.apply]] for validated construction, [[TopicPattern.unsafe]] when the value is known to be valid, or
  * the `tp"..."` string interpolator for compile-time checked literals:
  * {{{
  *   val pattern = tp"orders.*"   // validated at compile time, zero runtime cost
  * }}}
  */
object TopicPattern:
  private val allowed = """^[a-zA-Z0-9._\-*#]+$""".r

  private def validate(pattern: String): Either[String, TopicPattern] = pattern match
    case p if p.isEmpty                      => Left("TopicPattern must not be empty")
    case p if p.length > 255                 => Left(s"TopicPattern must be at most 255 characters, got ${p.length}")
    case p if allowed.findFirstIn(p).isEmpty =>
      Left("TopicPattern contains invalid characters (allowed: a-zA-Z0-9._-*#)")
    case p if p.startsWith(".") => Left("TopicPattern must not start with a dot")
    case p if p.endsWith(".")   => Left("TopicPattern must not end with a dot")
    case p if p.contains("..")  => Left("TopicPattern must not contain consecutive dots")
    case p if p.contains("**")  =>
      Left("TopicPattern must not contain consecutive stars (**), use # for multi-segment matching")
    case p if p.contains("##") =>
      Left("TopicPattern must not contain consecutive hashes (##), a single # already matches zero or more segments")
    case p if p.contains("*#") || p.contains("#*") =>
      Left("TopicPattern must not contain adjacent wildcards (*# or #*), separate wildcards with dots")
    case _ => Right(pattern)

  def apply(pattern: String): Either[String, TopicPattern] = validate(pattern)

  def unsafe(pattern: String): TopicPattern =
    val result = validate(pattern)
    require(result.isRight, result.left.getOrElse(""))
    pattern

  private[pgmq4s] def trusted(pattern: String): TopicPattern = pattern

  extension (topicPattern: TopicPattern) def value: String = topicPattern

private[pgmq4s] object TopicPatternMacro:
  def impl(sc: Expr[StringContext], @scala.annotation.unused args: Expr[Seq[Any]])(using
      Quotes
  ): Expr[TopicPattern] =
    import quotes.reflect.*
    sc match
      case '{ StringContext(${ Varargs(Exprs(parts)) }*) } =>
        if parts.size != 1 then report.errorAndAbort("tp\"...\" does not support interpolation")
        val pattern = parts.head
        TopicPattern(pattern) match
          case Right(_)       => '{ TopicPattern.trusted(${ Expr(pattern) }) }
          case Left(errorMsg) => report.errorAndAbort(errorMsg)
      case _ =>
        report.errorAndAbort("tp\"...\" requires a string literal")

extension (inline sc: StringContext)
  inline def tp(inline args: Any*): TopicPattern = ${ TopicPatternMacro.impl('sc, 'args) }
