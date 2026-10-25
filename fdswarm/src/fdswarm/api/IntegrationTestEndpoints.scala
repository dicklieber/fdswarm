integratopinpackage fdswarm.api

import cats.effect.IO
import fdswarm.{QsoMetadataManager, StartupInfo}
import fdswarm.contestStart.ContestStartManager
import fdswarm.fx.contest.{ContestConfig, ContestConfigManager, ContestTypes}
import fdswarm.model.{BandMode, Callsign, Exchange, Qso}
import fdswarm.replication.StatusBroadcastService
import fdswarm.store.QsoStore
import io.circe.Codec
import jakarta.inject.{Inject, Singleton}
import javafx.application.Platform
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.json.circe.*
import sttp.tapir.server.ServerEndpoint

import java.time.Instant

case class IntegrationQso(callsign: String, exchange: String, bandMode: String, stamp: Instant)
    derives Codec.AsObject, Schema
case class IntegrationContest(name: String, ourCallsign: String, transmitters: Int,
    ourClass: String, ourSection: String, club: String) derives Codec.AsObject, Schema

/** Absent from routing and Swagger unless --integration-test is explicitly supplied. */
@Singleton
final class IntegrationTestEndpoints @Inject()(startup: StartupInfo, store: QsoStore,
    metadata: QsoMetadataManager, contests: ContestConfigManager, types: ContestTypes,
    starts: ContestStartManager, broadcasts: StatusBroadcastService) extends ApiEndpoints:

  private def onFx[A](body: => A): IO[A] = IO.async_ { complete =>
    Platform.runLater(() =>
      try complete(Right(body))
      catch case error: Throwable => complete(Left(error)))
  }

  private[api] def authorized(token: String): Boolean =
    startup.integrationTest.exists(_.token == token)

  private def run[A](token: String)(body: => A): IO[Either[String, A]] =
    if !authorized(token) then IO.pure(Left("Invalid integration test token"))
    else onFx(body).map(Right(_))

  override def endpoints: List[ServerEndpoint[Any, IO]] =
    if startup.integrationTest.isEmpty then Nil
    else
      val base = endpoint.in("integration-test")
        .in(header[String]("X-Fdswarm-Test-Token"))
        .errorOut(statusCode(StatusCode.Forbidden)).errorOut(stringBody)
      List(
        base.get.in("ready").out(jsonBody[Boolean]).serverLogic[IO](token => run(token)(true)),
        base.post.in("qso").in(jsonBody[IntegrationQso]).out(jsonBody[Qso])
          .serverLogic[IO] { (token, request) => run(token) {
            val qso = Qso(Callsign(request.callsign), Exchange(request.exchange),
              BandMode(request.bandMode), metadata.current, request.stamp)
            val result = store.add(qso)
            require(result.css == "addQsoOk", result.text)
            qso
          } },
        base.post.in("contest").in(jsonBody[IntegrationContest]).out(jsonBody[ContestConfig])
          .serverLogic[IO] { (token, request) => run(token) {
            val config = contests.updateConfig(ContestConfig(types(request.name),
              Callsign(request.ourCallsign), request.transmitters, request.ourClass,
              request.ourSection, club = request.club))
            broadcasts.broadcastStatus()
            config
          } },
        base.post.in("start-contest").out(jsonBody[Instant])
          .serverLogic[IO](token => run(token) {
            starts.startContest()
            broadcasts.broadcastStatus()
            starts.contestStart.value.start
          })
      )
