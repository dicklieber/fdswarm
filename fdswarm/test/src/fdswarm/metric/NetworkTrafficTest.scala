package fdswarm.metric

import fdswarm.replication.Service
import io.dropwizard.metrics5.MetricRegistry
import munit.FunSuite

class NetworkTrafficTest extends FunSuite:
  private def traffic = new NetworkTraffic(new MetricRegistry)

  test("dependency injection supplies a singleton using the application's metric registry"):
    val injector = com.google.inject.Guice.createInjector()
    val first = injector.getInstance(classOf[NetworkTraffic])
    assert(first eq injector.getInstance(classOf[NetworkTraffic]))
    assert(NetworkTraffic.report(first.snapshot).isDefined)

  test("UDP byte totals and packet counts are separated by service and direction"):
    val meter = traffic
    meter.udp(Service.Status, "sent", 120)
    meter.udp(Service.Status, "sent", 130)
    meter.udp(Service.QSO, "received", 300)
    meter.udp(Service.SendStatus, "sent", 40)
    val report = NetworkTraffic.report(meter.snapshot).get
    assertEquals(report.usage("udp.status").sent, 250L)
    assertEquals(report.usage("udp.status").sentEvents, 2L)
    assertEquals(report.usage("udp.qso").received, 300L)
    assertEquals(report.usage("udp.qso").receivedEvents, 1L)
    assertEquals(report.usage("udp.other").sent, 40L)
    assertEquals(report.total.sent, 290L)
    assertEquals(report.total.received, 300L)

  test("HTTP attempts and completed QSO downloads are separate counts"):
    val meter = traffic
    (1 to 3).foreach(_ => meter.event("http.qso", "sent"))
    meter.qsoFetchApplied(0)
    meter.qsoFetchApplied(10)
    val report = NetworkTraffic.report(meter.snapshot).get
    assertEquals(report.usage("http.qso").sentEvents, 3L)
    assertEquals(report.completedFetches, 2L)
    assertEquals(report.unchangedFetches, 1L)

  test("reports reuse the transmitted meter counts and one-minute rates"):
    val metrics = Seq(MeterSnapshot("network.udp.status.sent.bytes", 4096L, 1024.0, 0.0, 0.0),
      MeterSnapshot("network.udp.status.received.bytes", 8192L, 2048.0, 0.0, 0.0))
    val report = NetworkTraffic.report(metrics).get
    assertEquals(report.total.sentRate, 1024.0)
    assertEquals(report.total.receivedRate, 2048.0)
    assertEquals(report.total.sent, 4096L)

  test("missing traffic metrics are unavailable rather than zero"):
    assertEquals(NetworkTraffic.report(Seq.empty), None)
    assertEquals(NetworkTraffic.report(Seq(CounterSnapshot("other", 10L))), None)

  test("HTTP paths identify QSO and config synchronization"):
    assertEquals(NetworkTraffic.httpCategory("/qsos"), "http.qso")
    assertEquals(NetworkTraffic.httpCategory("/contestConfig/"), "http.config")
    assertEquals(NetworkTraffic.httpCategory("/statusMessage"), "http.other")

  test("combining node usage preserves transmitted and received totals separately"):
    val a = NetworkTraffic.Usage(sent = 100, received = 300, sentRate = 10)
    val b = NetworkTraffic.Usage(sent = 200, received = 300, sentRate = 20)
    assertEquals((a + b).sent, 300L)
    assertEquals((a + b).received, 600L)
    assertEquals((a + b).sentRate, 30.0)
