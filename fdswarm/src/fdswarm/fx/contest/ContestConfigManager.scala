package fdswarm.fx.contest

import com.google.inject.name.Named
import fdswarm.io.FileHelper
import fdswarm.logging.LazyStructuredLogging
import fdswarm.model.Callsign
import fdswarm.replication.{NodeStatusDispatcher, Service}
import io.circe.generic.auto.{deriveDecoder, deriveEncoder}
import jakarta.inject.{Inject, Singleton}
import scalafx.application.Platform
import scalafx.beans.property.{ObjectProperty, ReadOnlyBooleanProperty, ReadOnlyBooleanWrapper}
import scalafx.scene.Node

@Singleton
final class ContestConfigManager @Inject() (
                                             fileHelper: FileHelper,
                                             filenameStamp: fdswarm.util.FilenameStamp,
                                             nodeStatusDispatcher: NodeStatusDispatcher,
                                             @Named("fdswarm.contestChangeIgnoreStatusSec")
    ignoreStatusSec: Int)
    extends ContestConfigFields with LazyStructuredLogging():
  lazy val hasConfiguration: ReadOnlyBooleanProperty = _hasConfiguration.readOnlyProperty
  private val file = "contest.json"
  private val _contestConfig: ObjectProperty[ContestConfig] = ObjectProperty(load())
  private val _hasConfiguration =
    new ReadOnlyBooleanWrapper(this, "hasConfiguration", _contestConfig.value.contestType != ContestType.NONE)

// These override methods expose the current value of the contestConfigProperty
  override def contestType: ContestType = contestConfigProperty.value.contestType

  override def ourCallsign: Callsign = contestConfigProperty.value.ourCallsign

  def contestConfigProperty: ObjectProperty[ContestConfig] = _contestConfig

  override def transmitters: Int = contestConfigProperty.value.transmitters

  override def ourClass: String = contestConfigProperty.value.ourClass

  _contestConfig.onChange((_, _, config) => _hasConfiguration.value = config.contestType != ContestType.NONE)

  nodeStatusDispatcher
    .addListener(service = Service.SyncContest)((_, newConfig) =>
      handleContestConfigUpdate(newConfig = newConfig))

  override def ourSection: String = contestConfigProperty.value.ourSection
  override def club: String = contestConfigProperty.value.club
  override def location: String = contestConfigProperty.value.location

  override def toolTip: Node = contestConfigProperty.value.toolTip

  def clearContestConfig(): Unit = setConfig(ContestConfig.noContest)

  def setConfig(newConfig: ContestConfig): Unit =
    _contestConfig.value = newConfig
    if newConfig.contestType == ContestType.NONE then removePersistedConfig() else persist()

  private def persist(): Unit =
    fileHelper.save(file, contestConfigProperty.value)

  private def removePersistedConfig(): Unit =
    fileHelper.remove(file)

  private def handleContestConfigUpdate(newConfig: ContestConfig): Unit =
    logger.debug("Received Contest", "ContestConfig" -> newConfig)
    Platform.runLater { setConfig(newConfig) }

  private def load(): ContestConfig =
    fileHelper.loadOrDefault[ContestConfig](file)(ContestConfig.noContest)
