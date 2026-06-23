package fdswarm.logging

import io.circe.{Json, JsonObject}
import org.apache.logging.log4j.{Level, LogManager, Logger, ThreadContext}

import java.io.PrintWriter
import java.io.StringWriter
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.{LinkedHashMap, UUID}

import scala.jdk.CollectionConverters.*
import scala.util.Using

final class StructuredLogger private (private val logger: Logger):

  def whenTraceEnabled[T](
                           body: => T,
                           orElse: => T = null.asInstanceOf[T]
                         ): T =
    if logger.isTraceEnabled then body else orElse

  def whenDebugEnabled[T](
                           body: => T,
                           orElse: => T = null.asInstanceOf[T]
                         ): T =
    if logger.isDebugEnabled then body else orElse

  def whenInfoEnabled[T](
                          body: => T,
                          orElse: => T = null.asInstanceOf[T]
                        ): T =
    if logger.isInfoEnabled then body else orElse

  def whenWarnEnabled[T](
                          body: => T,
                          orElse: => T = null.asInstanceOf[T]
                        ): T =
    if logger.isWarnEnabled then body else orElse

  def whenErrorEnabled[T](
                           body: => T,
                           orElse: => T = null.asInstanceOf[T]
                         ): T =
    if logger.isErrorEnabled then body else orElse

  def whenFatalEnabled[T](
                           body: => T,
                           orElse: => T = null.asInstanceOf[T]
                         ): T =
    if logger.isFatalEnabled then body else orElse

  def trace(message: String, args: (String, Any)*): Unit =
    if logger.isTraceEnabled then
      log(
        Level.TRACE,
        Some(message),
        None,
        args
      )

  def trace(
             args: (String, Any)*
           ): Unit =
    if logger.isTraceEnabled then
      log(
        Level.TRACE,
        None,
        None,
        args
      )

  def debug(message: String, args: (String, Any)*): Unit =
    if logger.isDebugEnabled then
      log(
        Level.DEBUG,
        Some(message),
        None,
        args
      )

  def debug(
             args: (String, Any)*
           ): Unit =
    if logger.isDebugEnabled then
      log(
        Level.DEBUG,
        None,
        None,
        args
      )

  def info(message: String, args: (String, Any)*): Unit =
    if logger.isInfoEnabled then
      log(
        Level.INFO,
        Some(message),
        None,
        args
      )

  def info(
            args: (String, Any)*
          ): Unit =
    if logger.isInfoEnabled then
      log(
        Level.INFO,
        None,
        None,
        args
      )

  def warn(message: String, args: (String, Any)*): Unit =
    if logger.isWarnEnabled then
      log(
        Level.WARN,
        Some(message),
        None,
        args
      )

  def warn(
            args: (String, Any)*
          ): Unit =
    if logger.isWarnEnabled then
      log(
        Level.WARN,
        None,
        None,
        args
      )

  def error(message: String, args: (String, Any)*): Unit =
    if logger.isErrorEnabled then
      log(
        Level.ERROR,
        Some(message),
        None,
        args
      )

  def error(
             args: (String, Any)*
           ): Unit =
    if logger.isErrorEnabled then
      log(
        Level.ERROR,
        None,
        None,
        args
      )

  def error(message: String, throwable: Throwable, args: (String, Any)*): Unit =
    if logger.isErrorEnabled then
      log(
        Level.ERROR,
        Some(message),
        Some(throwable),
        args
      )

  def error(
             throwable: Throwable,
             args: (String, Any)*
           ): Unit =
    if logger.isErrorEnabled then
      log(
        Level.ERROR,
        None,
        Some(throwable),
        args
      )

  def fatal(message: String, args: (String, Any)*): Unit =
    if logger.isFatalEnabled then
      log(
        Level.FATAL,
        Some(message),
        None,
        args
      )

  def fatal(
             args: (String, Any)*
           ): Unit =
    if logger.isFatalEnabled then
      log(
        Level.FATAL,
        None,
        None,
        args
      )

  def fatal(message: String, throwable: Throwable, args: (String, Any)*): Unit =
    if logger.isFatalEnabled then
      log(
        Level.FATAL,
        Some(message),
        Some(throwable),
        args
      )

  def fatal(
             throwable: Throwable,
             args: (String, Any)*
           ): Unit =
    if logger.isFatalEnabled then
      log(
        Level.FATAL,
        None,
        Some(throwable),
        args
      )

  private def log(
                   level: Level,
                   message: Option[String],
                   throwable: Option[Throwable],
                   args: Seq[(String, Any)]
                 ): Unit =
    val contextValues = buildContextValues(throwable, args)
    val eventJson = buildEventJson(level, message, contextValues)
    Using.resource(withThreadContext(contextValues)): _ =>
      logger.log(level, eventJson)

  private def buildContextValues(
                                  throwable: Option[Throwable],
                                  args: Seq[(String, Any)]
                                ): Seq[(String, String)] =
    val entries = LinkedHashMap[String, String]()

    throwable.foreach: t =>
      entries.put("error.type", t.getClass.getName)
      entries.put("error.message", safeMessage(t))
      entries.put("error.stack_trace", stackTraceToString(t))

    normalizeArgs(args).foreach: (key, value) =>
      entries.put(key, value)

    entries.asScala.toSeq

  private def normalizeArgs(args: Seq[(String, Any)]): Seq[(String, String)] =
    args.flatMap(expandField).map: (key, value) =>
      key -> valueAsString(toJsonValue(value))

  private def withThreadContext(
                                 values: Seq[(String, String)]
                               ): AutoCloseable =
    val previousValues = LinkedHashMap[String, String]()

    values.foreach: (key, value) =>
      val previousValue = ThreadContext.get(key)
      if previousValue != null then
        previousValues.put(key, previousValue)
      ThreadContext.put(key, value)

    () =>
      values.reverseIterator.foreach: (key, _) =>
        ThreadContext.remove(key)
        val previousValue = previousValues.get(key)
        if previousValue != null then
          ThreadContext.put(key, previousValue)

  private def expandField(field: (String, Any)): Seq[(String, Any)] =
    field match
      case (name, null) =>
        Seq(name -> null)

      case (_, fields: LogFields) =>
        fields.logFields.flatMap(expandField)

      case (name, opt: Option[?]) =>
        opt match
          case Some(value) => expandField(name -> value)
          case None        => Seq(name -> null)

      case (name, m: scala.collection.Map[?, ?]) =>
        Seq(name -> mapToJavaMap(m))

      case (name, arr: Array[?]) =>
        Seq(name -> arr.iterator.map(toJsonValue).toList.asJava)

      case (name, it: Iterable[?]) =>
        Seq(name -> it.iterator.map(toJsonValue).toList.asJava)

      case other =>
        Seq(other)

  private def toJsonValue(value: Any): Object =
    value match
      case null           => null
      case s: String      => s
      case c: Char        => c.toString
      case b: Boolean     => Boolean.box(b)
      case n: Byte        => Byte.box(n)
      case n: Short       => Short.box(n)
      case n: Int         => Int.box(n)
      case n: Long        => Long.box(n)
      case n: Float       => Float.box(n)
      case n: Double      => Double.box(n)
      case n: BigInt      => n.bigInteger
      case n: BigDecimal  => n.bigDecimal
      case uuid: UUID     => uuid.toString

      case instant: Instant =>
        DateTimeFormatter.ISO_INSTANT.format(instant)

      case date: LocalDate =>
        DateTimeFormatter.ISO_LOCAL_DATE.format(date)

      case time: LocalTime =>
        DateTimeFormatter.ISO_LOCAL_TIME.format(time)

      case dt: LocalDateTime =>
        DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(dt)

      case odt: OffsetDateTime =>
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(odt)

      case zdt: ZonedDateTime =>
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(zdt.toOffsetDateTime)

      case duration: Duration =>
        duration.toString

      case period: Period =>
        period.toString

      case enumValue: Enum[?] =>
        enumValue.name()

      case fields: LogFields =>
        mapFromPairs(fields.logFields)

      case m: scala.collection.Map[?, ?] =>
        mapToJavaMap(m)

      case arr: Array[?] =>
        arr.iterator.map(toJsonValue).toList.asJava

      case it: Iterable[?] =>
        it.iterator.map(toJsonValue).toList.asJava

      case product: Product =>
        val pairs =
          product.productElementNames
            .zip(product.productIterator)
            .toSeq
        mapFromPairs(pairs)

      case other =>
        other.toString

  private def mapFromPairs(fields: Seq[(String, Any)]): java.util.Map[String, Object] =
    val m = LinkedHashMap[String, Object]()
    fields.foreach: (key, value) =>
      m.put(key, toJsonValue(value))
    m

  private def mapToJavaMap(m: scala.collection.Map[?, ?]): java.util.Map[String, Object] =
    val out = LinkedHashMap[String, Object]()
    m.foreach: (k, v) =>
      out.put(String.valueOf(k), toJsonValue(v))
    out

  private def valueAsString(value: Object): String =
    value match
      case null =>
        null

      case m: java.util.Map[?, ?] =>
        m.asScala.iterator
          .map { case (k, v) =>
            s"${String.valueOf(k)}=${valueAsString(v.asInstanceOf[Object])}"
          }
          .mkString("{", ", ", "}")

      case it: java.lang.Iterable[?] =>
        it.asScala.iterator
          .map(v => valueAsString(v.asInstanceOf[Object]))
          .mkString("[", ", ", "]")

      case other =>
        String.valueOf(other)

  private def safeMessage(t: Throwable): String =
    Option(t.getMessage).getOrElse(t.getClass.getSimpleName)

  private def stackTraceToString(t: Throwable): String =
    val sw = StringWriter()
    val pw = PrintWriter(sw)
    try
      t.printStackTrace(pw)
      pw.flush()
      sw.toString
    finally
      pw.close()

  private def buildEventJson(
                              level: Level,
                              message: Option[String],
                              contextValues: Seq[(String, String)]
                            ): String =
    val topLevelFields =
      LogEventFieldNames.topLevelStringFields(timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
          level = level.name(),
          logger = logger.getName,
          message = message)
        .map: (key, value) =>
          key -> Json.fromString(value)

    val contextFields =
      contextValues.map: (key, value) =>
        key -> stringToJsonValue(value)

    Json
      .fromJsonObject(
        JsonObject.fromIterable(
          topLevelFields ++ contextFields
        )
      )
      .noSpaces

  private def stringToJsonValue(
                                 value: String
                               ): Json =
    if value == null then Json.Null
    else Json.fromString(value)

object StructuredLogger:
  def apply(clazz: Class[?]): StructuredLogger =
    new StructuredLogger(LogManager.getLogger(clazz))

  def apply(name: String): StructuredLogger =
    new StructuredLogger(LogManager.getLogger(name))
