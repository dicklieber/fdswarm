package fdswarm.metric

import fdswarm.fx.utils.StyledDialog
import fdswarm.replication.status.SwarmData
import fdswarm.util.NodeIdentityManager
import jakarta.inject.{Inject, Named, Singleton}
import scalafx.Includes.*
import scalafx.animation.{KeyFrame, Timeline}
import scalafx.geometry.Insets
import scalafx.scene.control.{ButtonType, Label, ScrollPane}
import scalafx.scene.layout.{GridPane, Region, VBox}
import scalafx.stage.Window
import scalafx.util.Duration
import java.time.Instant

@Singleton
final class NetworkTrafficDialog @Inject() (
    traffic: NetworkTraffic,
    swarmData: SwarmData,
    @Named("fdswarm.broadcastPeriodSec") broadcastPeriodSec: Int):

  def show(ownerWindow: Window): Unit =
    val contents = new VBox(12):
      padding = Insets(12)
      minWidth = Region.USE_PREF_SIZE
    val dialog = new StyledDialog[ButtonType]:
      title = "Network Traffic"
      headerText = "Swarm UDP and application HTTP traffic"
      initOwner(ownerWindow)
      resizable = true
    dialog.dialogPane().buttonTypes = Seq(ButtonType.Close)
    dialog.dialogPane().content = new ScrollPane:
      content = contents
      // Preserve every column; narrow windows scroll instead of clipping text.
      fitToWidth = false
      prefViewportWidth = 1000
      prefViewportHeight = 650

    def label(text: String): Label = new Label(text):
      minWidth = Region.USE_PREF_SIZE
      minHeight = Region.USE_PREF_SIZE

    def note(text: String): Label = new Label(text):
      wrapText = true
      maxWidth = 950
      minHeight = Region.USE_PREF_SIZE

    def grid(usage: Map[String, NetworkTraffic.Usage], stale: Boolean = false): GridPane =
      val result = new GridPane:
        hgap = 16
        vgap = 6
        minWidth = Region.USE_PREF_SIZE
      Seq("Traffic", "Sent KiB/s", "Received KiB/s", "Sent MiB", "Received MiB",
        "Sent packets / requests", "Received packets / requests").zipWithIndex.foreach { (text, col) =>
        val heading = label(text)
        heading.style = "-fx-font-weight: bold;"
        result.add(heading, col, 0)
      }
      val rows = NetworkTraffic.categories.map { (key, name) => name -> usage.getOrElse(key, NetworkTraffic.Usage()) }
      (rows :+ ("Total" -> usage.values.foldLeft(NetworkTraffic.Usage())(_ + _)))
        .zipWithIndex.foreach { case ((name, value), row) =>
          val values = Seq(name,
            if stale then "Stale" else f"${value.sentRate / 1024}%.3f",
            if stale then "Stale" else f"${value.receivedRate / 1024}%.3f",
            f"${value.sent / 1048576.0}%.3f", f"${value.received / 1048576.0}%.3f",
            value.sentEvents.toString, value.receivedEvents.toString)
          values.zipWithIndex.foreach { (text, col) => result.add(label(text), col, row + 1) }
        }
      result

    def refresh(): Unit =
      val now = Instant.now()
      val staleAfter = math.max(30L, broadcastPeriodSec.toLong * 3)
      val local = NetworkTraffic.report(traffic.snapshot).get
      val remotes = swarmData.allNodeStatuses
        .filterNot(_.nodeIdentity.instanceId == NodeIdentityManager.nodeIdentity.instanceId)
        .sortBy(status => (status.nodeIdentity.hostName, status.nodeIdentity.instanceId))
        .map { status =>
          val age = math.max(0L, java.time.Duration.between(status.received, now).getSeconds)
          (status, age, NetworkTraffic.report(status.statusMessage.metrics))
        }
      val active = local +: remotes.collect { case (_, age, Some(report)) if age <= staleAfter => report }
      val combined = NetworkTraffic.categories.map { (category, _) =>
        category -> active.map(_.usage(category)).foldLeft(NetworkTraffic.Usage())(_ + _)
      }.toMap
      val total = combined.values.foldLeft(NetworkTraffic.Usage())(_ + _)
      contents.children.clear()
      contents.children ++= Seq(
        note("Counts swarm UDP and built-in HTTP traffic; radio integrations such as rigctl and WSJT-X are excluded. " +
          "Rates are one-minute smoothed averages. Totals are since each node started; remote values reflect its latest status broadcast. " +
          "UDP includes the application datagram header. HTTP counts body bytes, including compression, but excludes HTTP and network headers, retransmissions and Wi-Fi airtime."),
        label(s"Active reporting nodes: ${active.size} of ${remotes.size + 1} known nodes"),
        label(f"Swarm traffic generated: ${total.sentRate / 1024}%.3f KiB/s; ${total.sent / 1048576.0}%.3f MiB (${total.sent} bytes) reported sent"),
        note("Swarm traffic generated is the Total sent column below. Received totals include repeated delivery of broadcasts; do not add sent and received to estimate swarm traffic. Stale/unavailable nodes are excluded from this summary."),
        note("Event counts are UDP packets or HTTP requests. HTTP sent requests count client attempts, including retries; received requests count calls to this node's server. HTTP byte totals include request and response bodies."),
        grid(combined),
        label(s"QSO downloads completed: ${active.map(_.completedFetches).sum}; added no QSOs: ${active.map(_.unchangedFetches).sum}"))

      def nodeHeading(node: fdswarm.util.NodeIdentity, suffix: String): Label =
        label(s"${node.hostName} — ${node.hostIp}:${node.port} — instance ${node.instanceId} — $suffix")
      contents.children ++= Seq(nodeHeading(NodeIdentityManager.nodeIdentity, "this node, live"), grid(local.usage),
        label(s"QSO downloads completed: ${local.completedFetches}; added no QSOs: ${local.unchangedFetches}"))
      remotes.foreach { (status, age, report) =>
        val stale = age > staleAfter
        contents.children += nodeHeading(status.nodeIdentity, s"last report ${age}s ago${if stale then " (stale)" else ""}")
        report match
          case Some(value) =>
            contents.children ++= Seq(grid(value.usage, stale),
              label(s"QSO downloads completed: ${value.completedFetches}; added no QSOs: ${value.unchangedFetches}"))
          case None => contents.children += label("Traffic metrics unavailable; this node may be running an older version.")
      }

    val timer = new Timeline:
      cycleCount = Timeline.Indefinite
      keyFrames = Seq(KeyFrame(Duration(2000), onFinished = _ => refresh()))
    refresh()
    timer.play()
    try dialog.showAndWait()
    finally timer.stop()
