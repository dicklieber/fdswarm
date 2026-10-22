package fdswarm.fx.contest

import fdswarm.model.Callsign
import fdswarm.replication.NodeStatusDispatcher
import fdswarm.support.TempDirFileHelperSuite
import fdswarm.util.FilenameStamp
import org.mockito.Mockito.mock

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
