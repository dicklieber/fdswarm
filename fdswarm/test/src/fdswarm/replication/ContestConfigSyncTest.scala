package fdswarm.replication

import fdswarm.fx.contest.{ContestConfig, ContestConfigVersion, ContestTypes}
import fdswarm.model.{BandMode, BandModeOperator, Callsign}
import fdswarm.util.NodeIdentity
import munit.FunSuite
import java.time.Instant

class ContestConfigSyncTest extends FunSuite:
  private val localNode = NodeIdentity("127.0.0.1", 8090, "local", "local")
  private val remoteNode = NodeIdentity("127.0.0.2", 8090, "remote", "remote")
  private val otherNode = NodeIdentity("127.0.0.3", 8090, "other", "other")
  private def version(nanos: Long) = ContestConfigVersion(
    ContestConfig(ContestTypes.configured("WFD"), Callsign("W1AW"), 1, "O", "CT")
      .updated(Instant.EPOCH.plusNanos(nanos)))
  private def status(node: NodeIdentity, config: Option[ContestConfigVersion]) =
    NodeStatus(StatusMessage(StoreStats(), BandModeOperator(Callsign("W1AW"), BandMode("20M SSB")),
      config, Instant.EPOCH, Seq.empty), node, isLocal = false)

  test("selects the newest remote advertisement regardless of arrival order or duplicates"):
    val older = status(remoteNode, Some(version(127)))
    val newer = status(otherNode, Some(version(128)))
    Seq(Seq(older, newer), Seq(newer, older), Seq(older, newer, older)).foreach { statuses =>
      assertEquals(ContestConfigSync.latestCandidate(statuses, Some(version(1)), localNode),
        Some(otherNode -> version(128)))
    }

  test("same and older versions require no HTTP request"):
    val current = version(128)
    assertEquals(ContestConfigSync.latestCandidate(Seq(status(remoteNode, Some(current)),
      status(otherNode, Some(version(127)))), Some(current), localNode), None)

  test("ignores local, unconfigured and malformed advertisements"):
    val statuses = Seq(status(localNode, Some(version(999))), status(remoteNode, None),
      status(otherNode, Some(ContestConfigVersion("bad", "!"))))
    assertEquals(ContestConfigSync.latestCandidate(statuses, None, localNode), None)

  test("a reconnecting or unconfigured node can discover the newest configuration"):
    val statuses = Seq(status(remoteNode, Some(version(128))))
    assertEquals(ContestConfigSync.latestCandidate(statuses, None, localNode), Some(remoteNode -> version(128)))
    assertEquals(ContestConfigSync.latestCandidate(statuses, Some(version(1)), localNode), Some(remoteNode -> version(128)))
