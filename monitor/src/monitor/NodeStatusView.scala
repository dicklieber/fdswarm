package monitor

import jakarta.inject.{Inject, Singleton}
import scalafx.Includes.*
import scalafx.beans.binding.Bindings
import scalafx.beans.property.StringProperty
import scalafx.collections.ObservableBuffer
import scalafx.geometry.Insets
import scalafx.scene.control.*
import scalafx.scene.layout.BorderPane

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}

@Singleton
final class NodeStatusView @Inject()():
  private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

  def content(nodes: ObservableBuffer[NodeData]): BorderPane =
    new BorderPane:
      center = table(nodes)
      padding = Insets(10)

  private def table(nodes: ObservableBuffer[NodeData]): TableView[NodeData] =
    val table: TableView[NodeData] = new TableView[NodeData](nodes):
      columnResizePolicy = TableView.ConstrainedResizePolicy
      columns ++= Seq(
        new TableColumn[NodeData, String]:
          text = "Host"
          cellValueFactory = c => StringProperty(c.value.nodeIdentity.shortHost)
          prefWidth = 200
        ,
        new TableColumn[NodeData, String]:
          text = "Last Status"
          cellValueFactory = c => Bindings.createStringBinding(() => timeFormatter.format(c.value.lastStatus.value), c.value.lastStatus)
          prefWidth = 180
      )
    table
