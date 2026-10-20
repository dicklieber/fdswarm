package fdswarm.scoring

import fdswarm.fx.contest.{ContestConfigManager, ContestType}
import jakarta.inject.*
import scalafx.beans.binding.{Bindings, BooleanBinding}
import scalafx.beans.property.ObjectProperty
import scalafx.collections.ObservableBuffer
import scalafx.geometry.Insets
import scalafx.scene.control.*
import scalafx.scene.layout.{GridPane, VBox}

@Singleton
class ContestScoringConfigPane @Inject() (
                                           contestConfigManager: ContestConfigManager,
                                           contestScoringConfigManager: ContestScoringConfigManager
                                         ):

  private val configProperty: ObjectProperty[ContestScoringConfig] =
    contestScoringConfigManager.contestScoringConfigProperty

  private val contestTypeValueLabel = new Label:
    text = contestConfigManager.contestConfigProperty.value.contestType.toString

  private val powerWattsField = new TextField:
    text = configProperty.value.powerWatts.toString
    prefColumnCount = 6

  private val powerWattsLabel = new Label("Power watts")

  private val powerSourceCombo = new ComboBox[PowerSource]:
    items = ObservableBuffer.from(PowerSource.values.toSeq)
    value = configProperty.value.powerSource

  private val powerSourceLabel = new Label("Power source")

  private val includeBonusesCheck = new CheckBox("Include bonuses in live score"):
    selected = configProperty.value.includeBonusesInLiveScore

  private val awayFromHomeCheck =
    objectiveCheckBox("away-from-home", "Away from home")
  private val qrpCheck =
    objectiveCheckBox("qrp", "QRP")
  private val alternativePowerCheck =
    objectiveCheckBox("alternative-power", "Alternative power")
  private val multipleAntennasCheck =
    objectiveCheckBox("multiple-antennas", "Multiple antennas")
  private val satelliteQsoCheck =
    objectiveCheckBox("satellite-qso", "Satellite QSO")
  private val winlinkMessageCheck =
    objectiveCheckBox("winlink-message", "Winlink message")
  private val copyBulletinCheck =
    objectiveCheckBox("copy-bulletin", "Copy bulletin")

  private val objectivesBox = new VBox:
    spacing = 6
    children = Seq(
      new Label("Claimed objectives"),
      awayFromHomeCheck,
      qrpCheck,
      alternativePowerCheck,
      multipleAntennasCheck,
      satelliteQsoCheck,
      winlinkMessageCheck,
      copyBulletinCheck
    )

  private val rootPane = new VBox:
    spacing = 12
    padding = Insets(12)
    children = Seq(
      new GridPane:
        hgap = 10
        vgap = 10
        add(
          powerWattsLabel,
          0,
          1
        )
        add(
          powerWattsField,
          1,
          1
        )
        add(
          powerSourceLabel,
          0,
          2
        )
        add(
          powerSourceCombo,
          1,
          2
        ),
        includeBonusesCheck,
      objectivesBox
    )

  refreshContestTypeUi()
  bindUiToCurrentConfig()
  installListeners()

  def pane: VBox =
    rootPane

  def saveDisabledBinding: BooleanBinding =
    Bindings.createBooleanBinding(
      () => !hasUnsavedChanges,
      powerWattsField.text,
      powerSourceCombo.value,
      includeBonusesCheck.selected,
      awayFromHomeCheck.selected,
      qrpCheck.selected,
      alternativePowerCheck.selected,
      multipleAntennasCheck.selected,
      satelliteQsoCheck.selected,
      winlinkMessageCheck.selected,
      copyBulletinCheck.selected
    )

  def validateInput(): Either[String, ContestScoringConfig] =
    uiConfigFromUi

  def saveFromUi(): Either[String, ContestScoringConfig] =
    validateInput().map { newConfig =>
      contestScoringConfigManager.update(newConfig)
      newConfig
    }

  def reloadFromManager(): Unit =
    bindUiToCurrentConfig()
    refreshContestTypeUi()

  private def installListeners(): Unit =
    contestConfigManager.contestConfigProperty.onChange { (_, _, _) =>
      refreshContestTypeUi()
    }

    configProperty.onChange { (_, _, _) =>
      bindUiToCurrentConfig()
    }

  private def bindUiToCurrentConfig(): Unit =
    val cfg = configProperty.value
    powerWattsField.text = cfg.powerWatts.toString
    powerSourceCombo.value = cfg.powerSource
    includeBonusesCheck.selected = cfg.includeBonusesInLiveScore

    setObjectiveSelected("  away-from-home", awayFromHomeCheck)
    setObjectiveSelected("qrp", qrpCheck)
    setObjectiveSelected("alternative-power", alternativePowerCheck)
    setObjectiveSelected("multiple-antennas", multipleAntennasCheck)
    setObjectiveSelected("satellite-qso", satelliteQsoCheck)
    setObjectiveSelected("winlink-message", winlinkMessageCheck)
    setObjectiveSelected("copy-bulletin", copyBulletinCheck)

  private def setObjectiveSelected(
                                    objectiveId: String,
                                    checkBox: CheckBox
                                  ): Unit =
    checkBox.selected = configProperty.value.claimedObjectives.contains(objectiveId)

  private def refreshContestTypeUi(): Unit =
    val contestType = contestConfigManager.contestConfigProperty.value.contestType
    contestTypeValueLabel.text = contestType.toString
    val showArrlFields = contestType.id == "WFD"
    val showWfdFields = !showArrlFields

    powerWattsLabel.visible = showArrlFields
    powerWattsLabel.managed = showArrlFields
    powerWattsField.visible = showArrlFields
    powerWattsField.managed = showArrlFields

    powerSourceLabel.visible = showArrlFields
    powerSourceLabel.managed = showArrlFields
    powerSourceCombo.visible = showArrlFields
    powerSourceCombo.managed = showArrlFields

    objectivesBox.visible = showWfdFields
    objectivesBox.managed = showWfdFields

  private def objectiveCheckBox(
                                 objectiveId: String,
                                 labelText: String
                               ): CheckBox =
    new CheckBox(labelText):
      selected = configProperty.value.claimedObjectives.contains(objectiveId)

  private def selectedObjectives: Set[String] =
    Set(
      "away-from-home" -> awayFromHomeCheck,
      "qrp" -> qrpCheck,
      "alternative-power" -> alternativePowerCheck,
      "multiple-antennas" -> multipleAntennasCheck,
      "satellite-qso" -> satelliteQsoCheck,
      "winlink-message" -> winlinkMessageCheck,
      "copy-bulletin" -> copyBulletinCheck
    ).collect { case (objectiveId, cb) if cb.selected.value => objectiveId }

  private def hasUnsavedChanges: Boolean =
    uiConfigFromUi.exists(_ != configProperty.value)

  private def uiConfigFromUi: Either[String, ContestScoringConfig] =
    parsePowerWatts(powerWattsField.text.value).map { watts =>
      ContestScoringConfig(
        powerWatts = watts,
        powerSource = powerSourceCombo.value.value,
        claimedObjectives = selectedObjectives,
        includeBonusesInLiveScore = includeBonusesCheck.selected.value
      )
    }

  private def parsePowerWatts(text: String): Either[String, Int] =
    val trimmed = text.trim
    if trimmed.isEmpty then
      Left("Power watts is required.")
    else
      scala.util.Try(trimmed.toInt).toOption match
        case None =>
          Left(s"Power watts must be a whole number, got: '$trimmed'.")
        case Some(value) if value < 0 =>
          Left("Power watts must be zero or greater.")
        case Some(value) =>
          Right(value)
