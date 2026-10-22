package fdswarm.fx.discovery

import fdswarm.fx.contest.*
import fdswarm.fx.sections.SectionsProvider
import fdswarm.fx.utils.{BootstrapIcons, StyledDialog}
import fdswarm.fx.utils.editor.{CallsignCustomField, CaseClassPropertyEditor, IntSpinner}
import fdswarm.replication.{Service, Transport}
import io.circe.syntax.EncoderOps
import jakarta.inject.Inject
import javafx.stage.Modality
import scalafx.Includes.*
import scalafx.beans.property.ObjectProperty
import scalafx.geometry.Pos
import scalafx.scene.control.{ButtonType, Label, ScrollPane, TitledPane}
import scalafx.scene.layout.{HBox, VBox}
import scalafx.scene.paint.Color

import java.nio.charset.StandardCharsets

/**
 * Dialog for configuring contest settings.
 */
class ContestConfigDialog @Inject() (
    contestCatalog: ContestCatalog,
    contestTypes: ContestTypes,
    sectionsProvider: SectionsProvider,
    contestManager: ContestConfigManager,
    exchangePane: ExchangePane,
    transport: Transport)
    extends StyledDialog[ButtonType]:

  private val configEditor: CaseClassPropertyEditor[ContestConfig] =
    new CaseClassPropertyEditor(contestManager.contestConfigProperty.value.getOrElse(
      ContestConfig(contestTypes.values.head, fdswarm.model.Callsign(""), 1, "-", "-")
    ))

  configEditor.setCustomEditor("contestType", new ContestChooser(contestTypes))
  configEditor.setCustomEditor("transmitters", new IntSpinner())
  configEditor.setCustomEditor("ourCallsign", new CallsignCustomField())
  private val contestTypeProperty: ObjectProperty[ContestType] = configEditor.getProperty("contestType")
  configEditor.setCustomEditor("ourClass", contestCatalog.comboBox(contestTypeProperty))
  configEditor.setCustomEditor("ourSection", new SectionComboBox(sectionsProvider))
  configEditor.hideField("timeStamp")
  configEditor.hideField("fingerPrint")

  private val typeEditor = new ContestTypeEditor(contestTypeProperty)
  private val typePane = new TitledPane:
    text = "Edit contest definition"
    expanded = false
    content = typeEditor

  private val configPane = configEditor.vertical
  private val currentContestConfigProperty = ObjectProperty(configEditor.finish())
  configEditor.onAnyFieldChange(() =>
    if configEditor.isValid then currentContestConfigProperty.value = configEditor.finish()
  )

  private val warningIcon = BootstrapIcons.svgPath(
    name = "exclamation-triangle-fill",
    size = 16,
    color = Color.web("#b8860b")
  )

  private val warningLabel = new Label:
    text = "This will change the contest on every node currently in the swarm!"
    textFill = Color.web("#664d03")
    style = "-fx-font-weight: 600;"
    wrapText = true

  private val warningPane = new HBox(spacing = 8):
    alignment = Pos.CenterLeft
    style =
      "-fx-background-color: #fff3cd; -fx-border-color: #ffecb5; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 8 10 8 10;"
    children ++= Seq(
      warningIcon,
      warningLabel
    )

  title = "Contest Configuration"
  initModality(Modality.APPLICATION_MODAL)
  resizable = true
  private val contents = new VBox(spacing = 8):
    children ++= Seq(
      configPane,
      typePane,
      exchangePane.pane(
        currentContestConfigProperty
      ),
      warningPane
    )
  dialogPane().content = new ScrollPane:
    content = contents
    fitToWidth = true
    prefViewportHeight = 560

  dialogPane().buttonTypes = Seq(ButtonType.OK, ButtonType.Cancel)

  private val updateButton = dialogPane().lookupButton(ButtonType.OK)
  private def updateValidity(): Unit =
    updateButton.setDisable(!configEditor.isValid || !typeEditor.valid.value)
  configEditor.onAnyFieldChange(() => updateValidity())
  typeEditor.valid.onChange { (_, _, _) => updateValidity() }
  updateValidity()
  updateButton.addEventFilter(
    javafx.event.ActionEvent.ACTION,
    (_: javafx.event.ActionEvent) =>
      val config = contestManager.updateConfig(configEditor.finish())
      val payload = config.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
      transport.send(
        service = Service.SyncContest,
        data = payload
      )
  )
