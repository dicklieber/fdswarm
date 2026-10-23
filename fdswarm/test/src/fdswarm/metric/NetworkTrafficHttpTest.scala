package fdswarm.metric

import cats.data.Kleisli
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fdswarm.util.Gzip
import fs2.Stream
import io.dropwizard.metrics5.MetricRegistry
import munit.FunSuite
import org.http4s.{HttpApp, Request, Response, Uri}

class NetworkTrafficHttpTest extends FunSuite:
  test("server counts streamed request bytes and compressed response bytes without changing either"):
    val traffic = new NetworkTraffic(new MetricRegistry)
    val compressed = Gzip.compress("Repeated QSO data ".repeat(100).getBytes(java.nio.charset.StandardCharsets.UTF_8))
    val requestBytes = Vector[Byte](1, 2, 3)
    val app: HttpApp[IO] = Kleisli { request =>
      request.body.compile.toVector.map { actual =>
        assertEquals(actual, requestBytes)
        Response[IO]().withBodyStream(Stream.emits(compressed).covary[IO])
      }
    }
    val response = new NetworkTrafficHttp(traffic).server(app)
      .run(Request[IO](uri = Uri.unsafeFromString("/qsos"))
        .withBodyStream(Stream.emits(requestBytes).covary[IO]))
      .flatMap(_.body.compile.toVector).unsafeRunSync()
    assertEquals(response, compressed.toVector)
    val usage = NetworkTraffic.report(traffic.snapshot).get.usage("http.qso")
    assertEquals(usage.received, 3L)
    assertEquals(usage.sent, compressed.length.toLong)
    assertEquals(usage.receivedEvents, 1L)

  test("streaming failures retain the count of bytes already consumed"):
    val traffic = new NetworkTraffic(new MetricRegistry)
    val middleware = new NetworkTrafficHttp(traffic)
    val stream = Stream.emits(Vector[Byte](1, 2, 3)).covary[IO] ++
      Stream.raiseError[IO](new RuntimeException("interrupted transfer"))
    val result = middleware.body(stream, "http.config", "received").compile.drain.attempt.unsafeRunSync()
    assert(result.isLeft)
    assertEquals(NetworkTraffic.report(traffic.snapshot).get.usage("http.config").received, 3L)
