package fdswarm.fx.contest

import com.google.inject.name.Named
import fdswarm.io.FileHelper
import fdswarm.logging.LazyStructuredLogging
import fdswarm.model.Callsign
import fdswarm.replication.{NodeStatusDispatcher, Service}
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
    extends LazyStructuredLogging():
  lazy val hasConfiguration: ReadOnlyBooleanProperty = _hasConfiguration.readOnlyProperty
  private val file = "contest.json"
  private val _contestConfig: ObjectProperty[Option[ContestConfig]] = ObjectProperty(load())
  private val _hasConfiguration =
    new ReadOnlyBooleanWrapper(this, "hasConfiguration", _contestConfig.value.isDefined)

  // Accessors for operations that require a configured contest.
  def contestType: ContestType = current.contestType

  def ourCallsign: Callsign = current.ourCallsign

  def current: ContestConfig =
    _contestConfig.value.getOrElse(throw new IllegalStateException("No contest configured"))

  def contestConfigProperty: ObjectProperty[Option[ContestConfig]] = _contestConfig

  def transmitters: Int = current.transmitters

  def ourClass: String = current.ourClass

  _contestConfig.onChange((_, _, config) => _hasConfiguration.value = config.isDefined)

  nodeStatusDispatcher
    .addListener(service = Service.SyncContest)((_, newConfig) =>
      handleContestConfigUpdate(newConfig = newConfig))

  def ourSection: String = current.ourSection
  def club: String = current.club
  def location: String = current.location

  def toolTip: Node = current.toolTip

  def clearContestConfig(): Unit =
    _contestConfig.value = None
    removePersistedConfig()

  def setConfig(newConfig: ContestConfig): Unit =
    _contestConfig.value = Some(newConfig)
    fileHelper.save(file, newConfig)

  private def removePersistedConfig(): Unit =
    fileHelper.remove(file)

  private def handleContestConfigUpdate(newConfig: ContestConfig): Unit =
    logger.debug("Received Contest", "ContestConfig" -> newConfig)
    Platform.runLater { setConfig(newConfig) }

  private def load(): Option[ContestConfig] =
    fileHelper.loadOrDefault[Option[ContestConfig]](file)(None)
