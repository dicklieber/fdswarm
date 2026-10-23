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
    for
      category = NetworkTraffic.httpCategory(request.uri.path.renderString)
      countedRequest = request.withBodyStream(body(request.body, category, "received"))
      _ <- IO(traffic.event(category, "received"))
      response <- app(countedRequest)
    yield response.withBodyStream(body(response.body, category, "sent"))
  }
