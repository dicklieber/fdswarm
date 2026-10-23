package fdswarm.metric

import cats.data.Kleisli
import cats.effect.IO
import fs2.Stream
import org.http4s.HttpApp

/** Observe chunks as they are consumed, preserving streaming and compressed sizes. */
final class NetworkTrafficHttp(traffic: NetworkTraffic):
  def body(stream: Stream[IO, Byte], category: String, direction: String): Stream[IO, Byte] =
    stream.chunks.evalTap(chunk => IO(traffic.bytes(category, direction, chunk.size))).unchunks

  def server(app: HttpApp[IO]): HttpApp[IO] = Kleisli { request =>
    val category = NetworkTraffic.httpCategory(request.uri.path.renderString)
    IO(traffic.event(category, "received")) *> app(request.withBodyStream(
      body(request.body, category, "received"))).map { response =>
      response.withBodyStream(body(response.body, category, "sent"))
    }
  }
