package fdswarm.replication

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fdswarm.api.ReplEndpoints
import fdswarm.fx.contest.{ContestConfigManager, ContestConfigVersion}
import fdswarm.logging.LazyStructuredLogging
import fdswarm.replication.status.SwarmData
import fdswarm.util.{NodeIdentity, NodeIdentityManager}
import jakarta.inject.{Inject, Singleton}
import scalafx.application.Platform
import scala.concurrent.duration.*
import scala.util.Try

/** Status packets advertise versions; HTTP transfers complete configurations. */
@Singleton
final class ContestConfigSync @Inject() (
    manager: ContestConfigManager,
    swarmData: SwarmData,
    dispatcher: NodeStatusDispatcher,
    callEndpoint: CallEndpoint) extends LazyStructuredLogging:

  // Confined to the FX thread, together with configuration adoption.
  private var fetching = false

  dispatcher.addListener(Service.Status, singleListener = false) { (node, status) =>
    Platform.runLater {
      synchronize(swarmData.allNodeStatuses :+ NodeStatus(status, node, isLocal = false))
    }
  }

  def discover(): Unit =
    Platform.runLater { synchronize(swarmData.allNodeStatuses) }

  private def synchronize(statuses: Seq[NodeStatus]): Unit =
    if fetching then return
    val local = manager.snapshot.map(ContestConfigVersion.apply)
    ContestConfigSync.latestCandidate(statuses, local, NodeIdentityManager.nodeIdentity).foreach { (node, advertised) =>
      fetching = true
      given NodeIdentity = node
      IO.defer(callEndpoint(ReplEndpoints.contestConfigDef, ()))
        .timeout(15.seconds)
        .attempt.flatMap { result =>
          IO {
            Platform.runLater {
              try
                result match
                  case Right(Some(config)) =>
                    val received = ContestConfigVersion(config)
                    if received >= advertised && config.withFingerPrint.fingerPrint == config.fingerPrint then
                      swarmData.rememberContestConfig(config)
                      manager.adoptIfNewer(config)
                  case Right(None) => () // The remote configuration was cleared.
                  case Left(error) => logger.warn(s"Unable to fetch contest configuration from $node: ${error.getMessage}")
              finally fetching = false
              // Further versions, failures and reconnects are retried by periodic status broadcasts.
            }
          }
        }.unsafeRunAndForget()
    }

object ContestConfigSync:
  private[replication] def latestCandidate(
      statuses: Seq[NodeStatus],
      local: Option[ContestConfigVersion],
      localIdentity: NodeIdentity): Option[(NodeIdentity, ContestConfigVersion)] =
    statuses.filter(_.nodeIdentity != localIdentity).flatMap { status =>
      status.statusMessage.contestConfig.flatMap { version =>
        Try {
          // Force decoding even when the local node has no configuration.
          version.epochNanos
          Option.when(version.isNewerThan(local))(status.nodeIdentity -> version)
        }.toOption.flatten
      }
    }.sortWith((a, b) => a._2 > b._2).headOption
