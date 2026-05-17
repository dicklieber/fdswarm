package fdswarm.scoring

import fdswarm.FdSwarmUi
import fdswarm.fx.contest.ContestConfigManager
import jakarta.inject.*
import scalafx.Includes.*
import scalafx.scene.control.{ButtonBar, ButtonType, Dialog}

@Singleton
class ContestScoringConfigDialog @Inject() (
                                             scoringPane: ContestScoringConfigPane,
                                             contestConfigManager: ContestConfigManager
                                           ):

  def show(): Unit =
    scoringPane.reloadFromManager()
    val saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OKDone)

    val dialog = new Dialog[ButtonType]:
      title = "Contest Scoring"
      headerText = s"Scoring settings for ${contestConfigManager.contestConfigProperty.value.contestType}"
      dialogPane().buttonTypes = Seq(ButtonType.Cancel, saveButtonType)
      dialogPane().content = scoringPane.pane
      if FdSwarmUi.primaryStage != null then initOwner(FdSwarmUi.primaryStage)
      resultConverter = (buttonType: ButtonType) => buttonType

    val saveButton = dialog.dialogPane().lookupButton(saveButtonType)
    saveButton.disable <== scoringPane.saveDisabledBinding

    val result = dialog.delegate.showAndWait()
    if result.isPresent && result.get == saveButtonType then
      scoringPane.saveFromUi()
    else
      scoringPane.reloadFromManager()
