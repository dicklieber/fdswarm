package fdswarm.store

import fdswarm.bands.{AvailableBandsManager, AvailableModesManager, BandModeBuilder}
import fdswarm.fx.contest.{ContestCatalog, ContestConfig, ContestConfigManager, ContestTypes}
import fdswarm.model.*
import fdswarm.replication.NodeStatusDispatcher
import fdswarm.support.TempDirFileHelperSuite
import fdswarm.util.{FilenameStamp, NodeIdentity, NodeIdentityManager}
import org.mockito.{ArgumentCaptor, Mockito}
import org.mockito.Mockito.{mock, verify, when}
import java.time.Instant

class BigQsosGeneratorTest extends TempDirFileHelperSuite:
  private val now = Instant.parse("2026-10-23T15:35:00.473310Z")
  private val node = NodeIdentity("127.0.0.1", 8080, "test", "test-node")

  private def generator(name: String, bands: Seq[Band] = Seq(Band.Band_20m),
      modes: Seq[Mode] = Seq(Mode.CW)): (BigQsosGenerator, QsoStore) =
    fileHelper.save("bands.json", bands)
    fileHelper.save("modes.json", modes)
    val types = ContestTypes.configured
    val manager = new ContestConfigManager(fileHelper, new FilenameStamp(), mock(classOf[NodeStatusDispatcher]), 5)
    manager.setConfig(ContestConfig(types(name), Callsign("WM9W"), 1,
      if name == "ARRL" then "A" else "O", "IL"))
    val identity = mock(classOf[NodeIdentityManager])
    when(identity.current).thenReturn(node)
    val store = mock(classOf[QsoStore])
    val service = new BigQsosGenerator(store, new BandModeBuilder, identity, manager,
      new ContestCatalog(types), new AvailableBandsManager(fileHelper), new AvailableModesManager(fileHelper))
    (service, store)

  private def capture(service: BigQsosGenerator, store: QsoStore, count: Int): Seq[Qso] =
    service.qsos(count, 2, "WA9", now)
    val captured = ArgumentCaptor.forClass(classOf[Seq[Qso]])
    verify(store).add(captured.capture())
    captured.getValue

  test("generated metadata and received classes follow the active contest"):
    val catalog = new ContestCatalog(ContestTypes.configured)
    Seq("ARRL", "WFD").foreach { name =>
      val (service, store) = generator(name)
      val batch = capture(service, store, 40)
      val allowed = catalog.getChars(ContestTypes.configured(name)).toSet
      assertEquals(batch.size, 40)
      assertEquals(batch.map(_.uuid).distinct.size, 40)
      batch.foreach { qso =>
        assertEquals(qso.qsoMetadata.contest, name)
        assertEquals(qso.qsoMetadata.node, node)
        assert(allowed.contains(qso.exchange.fdClass.toString.last))
      }
    }

  test("generator uses only configured bands and modes and preserves timestamp spacing"):
    val bands = Seq(Band.Band_23cm, Band.Band_3cm)
    val (service, store) = generator("ARRL", bands, Seq(Mode.DIGI))
    val batch = capture(service, store, 5)
    assertEquals(batch.map(_.callsign.value), Seq("WA9AAA", "WA9AAB", "WA9AAC", "WA9AAD", "WA9AAE"))
    assertEquals(batch.map(_.stamp), (0 until 5).map(i => now.minusSeconds(i * 1440L)))
    batch.foreach { qso =>
      assert(bands.contains(qso.bandMode.band))
      assertEquals(qso.bandMode.mode, Mode.DIGI)
      assert(BandMode.bandToFreq(qso.bandMode.band).nonEmpty)
    }

  test("empty band or mode selections fail before anything is added"):
    Seq((Seq.empty[Band], Seq(Mode.CW)), (Seq(Band.Band_20m), Seq.empty[Mode])).foreach { (bands, modes) =>
      val (service, store) = generator("ARRL", bands, modes)
      intercept[IllegalArgumentException](service.qsos(5, 2, "WA9", now))
      Mockito.verifyNoInteractions(store)
    }
