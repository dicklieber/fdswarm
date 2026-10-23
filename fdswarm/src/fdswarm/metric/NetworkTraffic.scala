package fdswarm.metric

import fdswarm.replication.Service
import io.dropwizard.metrics5.{MetricRegistry, SharedMetricRegistries}
import jakarta.inject.{Inject, Singleton}

/** Counts application bytes, not IP/TCP headers, retransmissions or Wi-Fi airtime. */
@Singleton
final class NetworkTraffic(registry: MetricRegistry):
  @Inject def this() = this(SharedMetricRegistries.getOrCreate("default"))

  import NetworkTraffic.*
  private val byteMeters = (for
    category <- categories.map(_._1)
    direction <- Seq("sent", "received")
  yield (category, direction) -> registry.meter(s"network.$category.$direction.bytes")).toMap
  private val eventCounters = (for
    category <- categories.map(_._1)
    direction <- Seq("sent", "received")
  yield (category, direction) -> registry.counter(s"network.$category.$direction.events")).toMap
  private val completed = registry.counter("network.qsoFetch.completed")
  private val noChange = registry.counter("network.qsoFetch.noChange")

  def bytes(category: String, direction: String, count: Int): Unit =
    byteMeters((category, direction)).mark(count.toLong)

  def event(category: String, direction: String): Unit =
    eventCounters((category, direction)).inc()

  def udp(service: Service[?], direction: String, count: Int): Unit =
    val category = service match
      case Service.Status => "udp.status"
      case Service.QSO => "udp.qso"
      case _ => "udp.other"
    bytes(category, direction, count)
    event(category, direction)

  def qsoFetchApplied(added: Int): Unit =
    completed.inc()
    if added == 0 then noChange.inc()

  def snapshot: Seq[MetricStat] =
    import scala.jdk.CollectionConverters.*
    registry.getMetrics.asScala.toSeq.filter(_._1.getKey.startsWith("network."))
      .flatMap { (name, metric) => MetricSnapshotFactory.fromMetric(metric, name.getKey) }

object NetworkTraffic:
  val categories: Vector[(String, String)] = Vector(
    "udp.status" -> "UDP status", "udp.qso" -> "UDP QSO", "udp.other" -> "UDP other",
    "http.qso" -> "HTTP QSO sync", "http.config" -> "HTTP config sync", "http.other" -> "HTTP other")

  def httpCategory(path: String): String = path.stripSuffix("/") match
    case "/qsos" => "http.qso"
    case "/contestConfig" => "http.config"
    case _ => "http.other"

  case class Usage(sent: Long = 0, received: Long = 0,
      sentRate: Double = 0, receivedRate: Double = 0,
      sentEvents: Long = 0, receivedEvents: Long = 0):
    def +(other: Usage): Usage = Usage(sent + other.sent, received + other.received,
      sentRate + other.sentRate, receivedRate + other.receivedRate,
      sentEvents + other.sentEvents, receivedEvents + other.receivedEvents)

  case class Report(usage: Map[String, Usage], completedFetches: Long, unchangedFetches: Long):
    def total: Usage = usage.values.foldLeft(Usage())(_ + _)

  def report(metrics: Seq[MetricStat]): Option[Report] =
    val meters = metrics.collect { case m: MeterSnapshot => m.metricName -> m }.toMap
    val counters = metrics.collect { case c: CounterSnapshot => c.metricName -> c.count }.toMap
    // Older nodes have no traffic instrumentation; do not present missing data as zero.
    if !meters.contains("network.udp.status.sent.bytes") then None
    else
      val usage = categories.map { (category, _) =>
        def meter(direction: String) = meters.get(s"network.$category.$direction.bytes")
        def events(direction: String) = counters.getOrElse(s"network.$category.$direction.events", 0L)
        category -> Usage(meter("sent").map(_.count).getOrElse(0L),
          meter("received").map(_.count).getOrElse(0L),
          meter("sent").map(_.m1).getOrElse(0.0), meter("received").map(_.m1).getOrElse(0.0),
          events("sent"), events("received"))
      }.toMap
      Some(Report(usage, counters.getOrElse("network.qsoFetch.completed", 0L),
        counters.getOrElse("network.qsoFetch.noChange", 0L)))
