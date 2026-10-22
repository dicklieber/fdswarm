package fdswarm.fx.contest

import fdswarm.model.Callsign
import fdswarm.replication.NodeStatusDispatcher
import fdswarm.support.TempDirFileHelperSuite
import fdswarm.util.FilenameStamp
import org.mockito.Mockito.mock
import io.circe.syntax.*

import java.time.Instant

class ContestConfigManagerTest extends TempDirFileHelperSuite:
  private def manager = new ContestConfigManager(
    fileHelper, new FilenameStamp(), mock(classOf[NodeStatusDispatcher]), 5
  )
  private def config = ContestConfig(ContestTypes.configured("WFD"), Callsign("W1AW"), 1, "O", "CT")

  test("local saves refresh metadata and persist the exact published version"):
    val service = manager
    val previous = config.updated(Instant.EPOCH)
    val saved = service.updateConfig(previous.copy(club = "Local club"))
    assert(saved.timeStamp.isAfter(previous.timeStamp))
    assertNotEquals(saved.fingerPrint, previous.fingerPrint)
    assertEquals(saved.withFingerPrint, saved)
    assertEquals(service.current, saved)
    assertEquals(fileHelper.loadOrDefault[Option[ContestConfig]]("contest.json")(None), Some(saved))
    assertEquals(manager.current, saved)

  test("adopting a remote version preserves its timestamp and fingerprint"):
    val service = manager
    val received = config.updated(Instant.parse("2026-10-22T12:00:00Z"))
    service.setConfig(received)
    assertEquals(service.current, received)
    assertEquals(manager.current, received)

  test("a delayed HTTP response cannot replace a newer configuration"):
    val service = manager
    val oldResponse = config.updated(Instant.EPOCH)
    val newer = config.copy(club = "Newer").updated(Instant.EPOCH.plusSeconds(1))
    service.setConfig(newer)
    assert(!service.adoptIfNewer(oldResponse))
    assertEquals(service.current, newer)
    assertEquals(service.snapshot, Some(newer))

  test("new remote versions are adopted once and invalid fingerprints are rejected"):
    val service = manager
    service.clearContestConfig()
    val received = config.updated(Instant.EPOCH)
    assert(!service.adoptIfNewer(received.copy(fingerPrint = "invalid")))
    assertEquals(service.snapshot, None)
    assert(service.adoptIfNewer(received))
    assert(!service.adoptIfNewer(received))
    assertEquals(manager.current, received)

  test("loading a configuration predating endTime refreshes its fingerprint without changing its timestamp"):
    val previous = config.updated(Instant.EPOCH)
    val legacyJson = previous.asJson.mapObject(_.remove("endTime")
      .add("fingerPrint", io.circe.Json.fromString("previous-schema")))
    fileHelper.save("contest.json", legacyJson)
    val loaded = manager.current
    assertEquals(loaded.timeStamp, previous.timeStamp)
    assertEquals(loaded.endTime, None)
    assertEquals(loaded.withFingerPrint, loaded)
