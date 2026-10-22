package fdswarm.replication.status

import fdswarm.fx.contest.ContestConfigManager
import fdswarm.fx.utils.{IconButton, StyledDialog}
import fdswarm.logging.LazyStructuredLogging
import fdswarm.replication.StatusBroadcastService
import jakarta.inject.{Inject, Singleton}
import scalafx.Includes.*
import scalafx.scene.control.{Button, ButtonType}
import scalafx.scene.paint.Color


@Singleton
class ContestConfigMismatchUi @Inject() (swarmData: SwarmData,
                                         statusBroadcastService: StatusBroadcastService,
                                         contestConfigManager: ContestConfigManager) extends LazyStructuredLogging():

  def warningButton(size: Double = 18): Button =
    val button = IconButton(
      name = "exclamation-triangle",
      size = size,
      tooltipText = "Not all nodes have the same Contest Configuration",
      color = Color.Red)
    button.visible <== swarmData.contestConfigMismatchProperty
    button.managed <== button.visible
    button.onAction = _ => showMismatchDialog()
    button

  def showMismatchDialog(): Unit =
    val allStatuses = swarmData.allNodeStatuses.sorted
    if allStatuses.isEmpty then return

    val dialog = new StyledDialog[ButtonType]:
      title = "Contest Configuration Mismatch"
      headerText = "Not all nodes have the same Contest Configuration"
      resizable = true
    dialog.dialogPane().buttonTypes = Seq(ButtonType.Close)
    dialog.dialogPane().content = swarmData.buildGridPane(
      fields =
        Seq(NodeDataField.HostName, NodeDataField.ContestType, NodeDataField.ContestCallsign, NodeDataField.Exchange),
      bottomRow = Some(SwarmData.BottomRow(
        label = "Sync Contest",
        cellBuilder = nodeIdentity =>
          val syncButton = IconButton(name = "repeat", tooltipText = "Use for all nodes")
          val baseStyle = """
                -fx-background-color: white;
                -fx-border-color: transparent;
                -fx-padding: 6;
              """
          val hoverStyle = """
                -fx-background-color: rgba(0,0,0,0.08);
                -fx-border-color: transparent;
                -fx-padding: 6;
              """
          syncButton.style = baseStyle
          syncButton.onMouseEntered = _ => syncButton.style = hoverStyle
          syncButton.onMouseExited = _ => syncButton.style = baseStyle
          syncButton.disable = swarmData.nodeMap.get(nodeIdentity).flatMap(_.contestConfig).isEmpty
          syncButton.onAction = _ =>
            swarmData.nodeMap.get(nodeIdentity).flatMap(_.contestConfig).foreach(contestConfig =>
              // Selecting an older definition is a new local edit so it can converge.
              contestConfigManager.updateConfig(contestConfig)
              statusBroadcastService.broadcastStatus())
          syncButton
      ))
    )
    dialog.showAndWait()
