package fdswarm.fx.qso

import fdswarm.JavaFxTestKit
import fdswarm.model.{BandMode, Callsign}
import fdswarm.store.DupInfo
import munit.FunSuite
import scala.jdk.CollectionConverters.*

class DupPanelTest extends FunSuite:
  test("exact duplicates show a distinct warning and beep once until the duplicate changes") {
    class TestDupPanel extends DupPanel(null, null, null):
      var beeps = 0
      override protected def beep(): Unit = beeps += 1
    val panel = JavaFxTestKit.runOnFx { new TestDupPanel }
    val pane = JavaFxTestKit.runOnFx { panel.pane() }
    def labels(node: javafx.scene.Node): Seq[String] = node match
      case label: javafx.scene.control.Label => Seq(label.getText)
      case parent: javafx.scene.Parent => parent.getChildrenUnmodifiable.asScala.toSeq.flatMap(labels)
      case _ => Seq.empty
    val call = Callsign("WA9ABC")
    val bandMode = BandMode("20m SSB")

    JavaFxTestKit.runOnFx { panel.showDuplicate(call, bandMode) }
    JavaFxTestKit.runOnFx {
      assert(labels(pane.delegate).contains("Duplicate"))
      assert(labels(pane.delegate).exists(_.contains("WA9ABC already logged")))
      assert(panel.showingPotentialDuplicatesProperty.value)
      assertEquals(panel.beeps, 1)
      panel.showDuplicate(call, bandMode)
    }
    JavaFxTestKit.runOnFx {
      assertEquals(panel.beeps, 1)
      panel.show(DupInfo(Seq(call), 1))
    }
    JavaFxTestKit.runOnFx {
      assert(labels(pane.delegate).contains("Potential Duplicates"))
      assertEquals(panel.beeps, 1)
      panel.showDuplicate(call, bandMode)
    }
    JavaFxTestKit.runOnFx {
      assertEquals(panel.beeps, 2)
      panel.showDuplicate(call, BandMode("40m SSB"))
    }
    JavaFxTestKit.runOnFx {
      assertEquals(panel.beeps, 3)
      panel.clear
    }
    JavaFxTestKit.runOnFx {
      assert(!pane.visible.value)
      assert(!panel.showingPotentialDuplicatesProperty.value)
    }
  }
