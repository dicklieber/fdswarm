/*
 * Copyright (c) 2026. Dick Lieber, WA9NNN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package fdswarm

import fdswarm.contestStart.StartContestDialog
import FdSwarmUi.isMac
import fdswarm.fx.bandmodes.BandsAndModesPane
import fdswarm.fx.contest.ContestConfigManager
import fdswarm.fx.discovery.ContestConfigDialog
import fdswarm.fx.station.StationEditor
import fdswarm.fx.tools.*
import fdswarm.fx.{AboutMenuItem, UserConfig, UserConfigEditor}
import fdswarm.logging.LazyStructuredLogging
import fdswarm.metric.StatsManager
import fdswarm.replication.status.ContestConfigMismatchUi
import fdswarm.scoring.{ContestScoreResultsDialog, ContestScoringConfigDialog}
import _root_.io.circe.parser.decode
import _root_.io.circe.syntax.EncoderOps
import jakarta.inject.Inject
import javafx.concurrent.Worker
import javafx.scene.control.{Menu as JfxMenu, MenuItem as JfxMenuItem}
import netscape.javascript.JSObject
import scalafx.application.Platform
import scalafx.beans.binding.Bindings
import scalafx.beans.property.{BooleanProperty, StringProperty}
import scalafx.scene.Scene
import scalafx.scene.control.*
import scalafx.scene.layout.{Priority, VBox}
import scalafx.scene.web.WebView
import scalafx.stage.{Stage, Window}

import scala.io.Source
final class FdSwarmMenus @Inject()(
                                   bandModeManagerPane: BandsAndModesPane,
                                   stationEditor: StationEditor,
                                   howManyDialogService: HowManyDialogService,
                                   loggingDialog: LoggingDialog,
                                   contestTimeDialog: ContestTimeDialog,
                                   aboutMenuItem: AboutMenuItem,
                                   userConfig: UserConfig,
                                   userConfigEditor: UserConfigEditor,
                                   exportDialog: ExportDialog,
                                   sectionsProvider: fdswarm.fx.sections.SectionsProvider,
                                   sectionPanel: fdswarm.fx.sections.SectionPanel,
                                   swarmStatusAdmin: fdswarm.fx.admin.SwarmStatusAdmin,
                                   summaryDialog: SummaryDialog,
                                   contestConfigDialog: ContestConfigDialog,
                                   contestScoringConfigDialog: ContestScoringConfigDialog,
                                   contestConfigManager: ContestConfigManager,
                                   metricsDialog: MetricsDialog,
                                   portsDialog: PortsDialog,
                                   statsManager: StatsManager,
                                   contestScoreResultsDialog: ContestScoreResultsDialog,
                                   startContestDialog: StartContestDialog,
                                   contestConfigMismatchUi: ContestConfigMismatchUi,
)
    extends LazyStructuredLogging:
  aboutMenuItem.onAction = _ => showAboutDialog()

  private lazy val configMenu: Menu =
    new Menu("Config"):
      items ++= Seq(
        new MenuItem("Band / Mode Manager"):
          onAction = _ => showBandModeManager()
        ,
        new MenuItem("Station"):
          onAction = _ =>
            stationEditor.show(
              FdSwarmUi.primaryStage
            )
        ,
        new SeparatorMenuItem(),
        new MenuItem("ARRL Sections Map"):
          onAction = _ =>
            showArrlSectionsMap(
              FdSwarmUi.primaryStage
            )
        ,
        labelArrlRegionsMenuItem,
        new SeparatorMenuItem(),
        new MenuItem("User Config"):
          onAction = _ =>
            userConfigEditor.show(
              FdSwarmUi.primaryStage
            )
        ,
        new MenuItem("Contest"):
          onAction = _ => contestConfigDialog.show()
        ,
        new MenuItem("Scoring"):
          onAction = _ => contestScoringConfigDialog.show()
        ,
        developerModeMenuItem
      )
  private val arrlRegionMapPath: os.Path = os.pwd / "arrl-region-map.json"
  private val labelArrlRegionsMenuItem = new CheckMenuItem(
    "Label ARRL Regions"
  ):
    selected = false
  private val developerModeMenuItem = new CheckMenuItem(
    "Developer Mode"
  )
  private val devMenu: Menu =
    new Menu("Dev"):
      visible = false
      items = Seq(
        new MenuItem("Generate QSOs"):
          onAction = _ =>
            howManyDialogService.showAndGenerate(
              FdSwarmUi.primaryStage
            )
        ,

        new MenuItem("Logging"):
          onAction = _ =>
            loggingDialog.show(
              FdSwarmUi.primaryStage
            )
        ,
        new MenuItem("Contest Time"):
          onAction = _ =>
            contestTimeDialog.show(
              FdSwarmUi.primaryStage
            )
        ,
        new MenuItem("Metrics"):
          onAction = _ =>
            metricsDialog.show(
              FdSwarmUi.primaryStage
            )
        ,
        new MenuItem("Ports"):
          onAction = _ =>
            portsDialog.show(
              FdSwarmUi.primaryStage
            )
        ,
        new MenuItem("Swarm Stats"):
          onAction = _ =>
            statsManager.show(
              FdSwarmUi.primaryStage
            )
        ,
        new MenuItem("Clear Contest"):
          onAction = _ => contestConfigManager.clearContestConfig()
        ,
        new SeparatorMenuItem(),
        new MenuItem("Contest Config Mismatch"):
          onAction = _ => contestConfigMismatchUi.showMismatchDialog()
      )
  private val adminMenu: Menu =
    new Menu("Admin"):
      items = Seq(
        new MenuItem("Swarm Status"):
          onAction = _ =>
            swarmStatusAdmin.show(
              FdSwarmUi.primaryStage
            )
        ,
        new MenuItem("Start Contest"):
          onAction = _ =>
            startContestDialog.showStartContestDialog(
              FdSwarmUi.primaryStage
            )
      )
  private val reportsMenu: Menu =
    new Menu("Reports"):
      items = Seq(
        new MenuItem("Score Results"):
          onAction = _ => contestScoreResultsDialog.show()
        ,
        new MenuItem("Summary"):
          onAction = _ =>
            summaryDialog.show(
              FdSwarmUi.primaryStage
            )

      )
  private var arrlRegionToSection: Map[String, String] = loadArrlRegionMap()

  def showAboutDialog(): Unit =
    aboutMenuItem.showAboutDialog(
      FdSwarmUi.primaryStage
    )

  def showBandModeManager(): Unit =
    bandModeManagerPane.show(
      FdSwarmUi.primaryStage
    )

  private def fileMenu: Menu =
    new Menu("File"):
      private val exportItem = new MenuItem("Export"):
        onAction = _ =>
          exportDialog.show(
            FdSwarmUi.primaryStage
          )
      private val exitItem = new MenuItem("Exit"):
        onAction = _ => Platform.exit()
      items =
        if isMac then Seq(exportItem)
        else
          Seq(
            exportItem,
            exitItem
          )

  developerModeMenuItem.selected <==> userConfig.getProperty[BooleanProperty](
    "developerMode"
  )
  devMenu.visible <== developerModeMenuItem.selected

  private def helpMenu: Menu =
    new Menu("Help"):
      items = Seq(aboutMenuItem)

  val menuBar: MenuBar =
    new MenuBar:
      useSystemMenuBar = isMac
      menus = Seq(
        fileMenu,
        reportsMenu,
        configMenu,
        adminMenu,
        devMenu,
        helpMenu
      )

  def withMenuItemsDisabled[A](body: => A): A =
    val items = allMenuItems
    val disabledStates = items.map(item => item -> item.isDisable)
    items.foreach(_.setDisable(true))
    try body
    finally disabledStates.foreach { case (item, wasDisabled) => item.setDisable(wasDisabled) }

  private def allMenuItems: Seq[JfxMenuItem] =
    menuBar.delegate.getMenus.toArray.toSeq.collect {
      case item: JfxMenuItem => item
    }.flatMap(menuAndChildren)

  private def menuAndChildren(item: JfxMenuItem): Seq[JfxMenuItem] =
    item match
      case menu: JfxMenu =>
        item +: menu.getItems.toArray.toSeq.collect {
          case child: JfxMenuItem => child
        }.flatMap(menuAndChildren)
      case _ => Seq(item)

  private def loadArrlRegionMap(): Map[String, String] =
    if os.exists(arrlRegionMapPath) then
      val txt:String = os.read(arg = arrlRegionMapPath)
      decode[Map[String, String]](txt) match
        case Right(m) =>
          m
        case Left(err) =>
          logger.warn("Could not parse", "Err" -> err.toString)
          Map.empty
    else
      Map.empty

  private def showArrlSectionsMap(
      parentWindow: Window
    ): Unit =
    val svgText:String =
      val res =
        getClass.getResourceAsStream("/maps/arrl_sections_autotrace.svg")
      if res != null then
        val source = Source.fromInputStream(
          res,
          "UTF-8"
        )
        try source.mkString
        finally source.close()
      else
        val dev = os.pwd / "arrl_sections_autotrace.svg"
        if os.exists(dev) then
          os.read(arg = dev)
        else
          """<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"800\" height=\"200\">
            <rect width=\"100%\" height=\"100%\" fill=\"#f7f7f7\"/>
            <text x=\"20\" y=\"110\" font-size=\"20\" fill=\"#333\" font-family=\"system-ui\">
              Missing /maps/arrl_sections_autotrace.svg (bundle it in resources)
            </text>
          </svg>""".stripMargin

    val webView = new WebView()

    final class JsBridge:
      private val allArrlSectionCodes: Seq[String] =
        sectionsProvider.allSections.map(_.code).distinct.sorted
      private var activeRegionId: Option[String] = None

      def getMappings: String =
        arrlRegionToSection.asJson.noSpaces

      def getAllSections: String =
        allArrlSectionCodes.asJson.noSpaces

      def isLabelingMode: Boolean =
        labelArrlRegionsMenuItem.selected.value

      def getActiveRegionId: Option[String] = activeRegionId

      def updateMapping(
          regionId: String,
          sectionCode: String
        ): Unit =
        Platform.runLater {
          val section = sectionCode.trim.toUpperCase
          if section.nonEmpty then
            arrlRegionToSection = arrlRegionToSection.updated(
              regionId,
              section
            )
            saveArrlRegionMap(arrlRegionToSection)
            logger.info(
              s"ARRL region mapped via SectionPanel: $regionId -> $section"
            )
            webView.engine.executeScript("refreshLabels();")
        }

      def sectionClicked(
          id: String
        ): Unit =
        Platform.runLater {
          val mapped = arrlRegionToSection.get(id)
          logger.info(
            s"ARRL map clicked: $id${mapped.fold("")(value => s" -> $value")}"
          )
          if labelArrlRegionsMenuItem.selected.value then
            activeRegionId = Some(id)
            webView.engine.executeScript(s"highlightRegion('$id');")
          webView.engine.executeScript("refreshLabels();")
        }

    val bridge = new JsBridge

    val mappingSectionField = new StringProperty("")
    val mappingCanSubmit = Bindings.createBooleanBinding(() => true)

    mappingSectionField.onChange {
      (
          _,
          _,
          newValue
        ) =>
        if newValue != null && newValue.nonEmpty then
          bridge.getActiveRegionId.foreach(regionId =>
            bridge.updateMapping(
              regionId,
              newValue
            )
          )
    }

    val sectionPanelNode = sectionPanel.buildNode(
      mappingSectionField,
      () => (),
      mappingCanSubmit,
      "Select ARRL Section to Map"
    )

    def wrap(
        svg: String
      ): String =
      s"""<!doctype html>
         |<html><head><meta charset=\"utf-8\"/>
         |<style>
         |  html, body { margin:0; padding:0; background:#ffffff; overflow: hidden; }
         |  svg { width:100vw; height:100vh; display:block; }
         |  .section-label {
         |    font-family: sans-serif;
         |    font-size: 14px;
         |    font-weight: bold;
         |    fill: black;
         |    pointer-events: none;
         |    text-anchor: middle;
         |    dominant-baseline: middle;
         |  }
         |  .section { cursor: pointer; }
         |  .section:hover { opacity: 0.8; }
         |  .active-region { stroke: red; stroke-width: 3px; }
         |</style>
         |</head><body>
         |$svg
         |<script>
         |  function highlightRegion(id) {
         |    document.querySelectorAll('.active-region').forEach(el => el.classList.remove('active-region'));
         |    const el = document.getElementById(id);
         |    if (el) el.classList.add('active-region');
         |  }
         |
         |  function refreshLabels() {
         |    if (!window.app) return;
         |    const mappings = JSON.parse(window.app.getMappings());
         |    const svg = document.querySelector('svg');
         |
         |    document.querySelectorAll('.section-label').forEach(el => el.remove());
         |
         |    for (const [regionId, sectionCode] of Object.entries(mappings)) {
         |      const path = document.getElementById(regionId);
         |      if (path) {
         |        const bbox = path.getBBox();
         |        const text = document.createElementNS('http://www.w3.org/2000/svg', 'text');
         |        text.setAttribute('x', bbox.x + bbox.width / 2);
         |        text.setAttribute('y', bbox.y + bbox.height / 2);
         |        text.setAttribute('class', 'section-label');
         |        text.textContent = sectionCode;
         |        svg.appendChild(text);
         |
         |        let title = path.querySelector('title');
         |        if (!title) {
         |          title = document.createElementNS('http://www.w3.org/2000/svg', 'title');
         |          path.appendChild(title);
         |        }
         |        title.textContent = sectionCode;
         |      }
         |    }
         |  }
         |
         |  function wireSectionClicks() {
         |    const els = document.querySelectorAll('.section');
         |    els.forEach(el => {
         |      el.addEventListener('click', () => {
         |        const id = el.id || el.getAttribute('data-section') || '';
         |        if (window.app && typeof window.app.sectionClicked === 'function') {
         |          window.app.sectionClicked(id);
         |        }
         |      });
         |    });
         |  }
         |</script>
         |</body></html>""".stripMargin

    webView.engine.loadContent(wrap(svgText))
    webView.engine.getLoadWorker.stateProperty.addListener {
      (
          _,
          _,
          state
        ) =>
        if state == Worker.State.SUCCEEDED then
          val window =
            webView.engine.executeScript("window").asInstanceOf[JSObject]
          window.setMember(
            "app",
            bridge
          )
          webView.engine.executeScript("wireSectionClicks();")
          webView.engine.executeScript("refreshLabels();")
    }

    val stage = new Stage:
      initOwner(parentWindow)
      title = "ARRL Sections Map"
      scene = new Scene(
        new VBox:
          children = Seq(
            webView,
            sectionPanelNode
          )
          VBox.setVgrow(
            webView,
            Priority.Always
          )
        ,
        1000,
        850
      )

    stage.show()

  private def saveArrlRegionMap(
      mappings: Map[String, String]
    ): Unit =
    try
      val json = mappings.asJson.spaces2
      os.write.over(
        arrlRegionMapPath,
        json
      )
    catch
      case e: Exception =>
        logger.warn(
          "Could not write",
          "Path" -> arrlRegionMapPath.toString
        )
