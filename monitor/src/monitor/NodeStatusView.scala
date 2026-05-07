package monitor

import jakarta.inject.{Inject, Singleton}
import scalafx.Includes.*
import scalafx.beans.binding.Bindings
import scalafx.beans.property.{ObjectProperty, StringProperty}
import scalafx.collections.ObservableBuffer
import scalafx.geometry.Insets
import scalafx.scene.control.*
import scalafx.scene.layout.BorderPane

import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Instant, ZoneId}

@Singleton
final class NodeStatusView @Inject()():
  private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
  private val integerFormatter = NumberFormat.getIntegerInstance()

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
        ,
        new TableColumn[NodeData, Number]:
          text = "Count"
          cellValueFactory = _.value.lastIndexItemCount.delegate
          prefWidth = 60
        ,
        new TableColumn[NodeData, Number]:
          text = "Offset"
          cellValueFactory = _.value.lastIndexOffset.delegate
          cellFactory = (_: TableColumn[NodeData, Number]) =>
            new TableCell[NodeData, Number]:
              private def refresh(value: Number): Unit =
                text = if empty.value || value == null then "" else integerFormatter.format(value.longValue())
              item.onChange { (_, _, value) => refresh(value) }
              empty.onChange { (_, _, _) => refresh(item.value) }
          prefWidth = 100
        ,
        new TableColumn[NodeData, String]:
          text = "Last Indexed"
          cellValueFactory = c => Bindings.createStringBinding(() => timeFormatter.format(c.value.lastIndexStamp.value), c.value.lastIndexStamp)
          prefWidth = 180
      )
    table
