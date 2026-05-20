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

package fdswarm.fx

import com.organization.BuildInfo.*
import com.typesafe.config.Config
import fdswarm.StartupInfo
import fdswarm.fx.utils.JsonPrettyPrinter
import fdswarm.io.FileHelper
import fdswarm.replication.Transport
import fdswarm.util.NodeIdentityManager
import io.circe.syntax.*
import jakarta.inject.Inject
import scalafx.Includes.*
import scalafx.geometry.{Insets, Pos}
import scalafx.scene.control.Alert.AlertType
import scalafx.scene.control.*
import scalafx.scene.input.{Clipboard, ClipboardContent}
import scalafx.scene.layout.{GridPane, HBox, Priority, VBox}
import scalafx.scene.paint.Color
import scalafx.scene.shape.SVGPath
import scalafx.stage.Window
class AboutMenuItem @Inject()(fileHelper: FileHelper,
                              transport: Transport,
                              startupInfo: StartupInfo,
                              config: Config)
  extends MenuItem("About"):
  def setOwner(window: Window): Unit =
    onAction = _ => showAboutDialog(window)

  private def getIconNode: Option[scalafx.scene.Group] = {
    try {
      val resource = getClass.getResource("/icons/fdswarm.svg")
      if (resource != null) {
        val svgContent = os.read(os.Path(java.nio.file.Paths.get(resource.toURI)))
        val pathRegex = "d=\"([^\"]+)\"".r
        val paths = pathRegex.findAllMatchIn(svgContent).map(_.group(1)).toList
        if (paths.nonEmpty) {
          val combinedPathValue = paths.mkString(" ")
          val svgPath = new SVGPath {
            content = combinedPathValue
            fill = Color.Black
          }
          val group = new scalafx.scene.Group(svgPath)
          // The SVG is 64x64. Let's make it about 80x80 or 100x100
          val scale = 0.75 
          group.scaleX = scale
          group.scaleY = scale
          Some(group)
        } else None
      } else None
    } catch {
      case _: Exception => None
    }
  }

  def showAboutDialog(window: Window): Unit =
    val grid = new GridPane:
      hgap = 10
      vgap = 10
      padding = Insets(20, 150, 10, 10)

    val iconNode = getIconNode
    val headerBox = new HBox {
      spacing = 20
      alignment = Pos.CenterLeft
      children = iconNode.toSeq ++ Seq(
        new VBox {
          children = Seq(
            new Label("FdSwarm") { style = "-fx-font-size: 24px; -fx-font-weight: bold;" },
            new Label(s"v$displayVersion")
          )
        }
      )
    }

    val dataPath = fileHelper.directory
    val dataFilesNode = if os.exists(dataPath) && os.isDir(dataPath) then
      val files = os.list(dataPath)
        .filter(p => os.isFile(p) && !p.last.startsWith("."))
        .map(_.last)
        .sorted
      new GridPane:
        hgap = 10
        vgap = 0
        files.zipWithIndex.foreach { (fileName, index) =>
          val col = index % 2
          val row = index / 2
          val link = new Hyperlink(fileName):
            onAction = _ =>
              val fileContent = try
                os.read(dataPath / fileName)
              catch
                case e: Exception => s"Error reading file: ${e.getMessage}"

              val alert = new Alert(AlertType.Information):
                initOwner(window)
                title = fileName
                headerText = s"Contents of $fileName"
                val copyButton = new Button("Copy to Clipboard"):
                  onAction = _ =>
                    val content = new ClipboardContent()
                    content.putString(fileContent)
                    Clipboard.systemClipboard.setContent(content)

                dialogPane().content = new VBox:
                  spacing = 10
                  children = Seq(
                    copyButton,
                    if fileName.endsWith(".json") || fileName.endsWith(".ndjson") then
                      new ScrollPane:
                        content = JsonPrettyPrinter.toTable(fileContent)
                        prefViewportHeight = 400
                        prefViewportWidth = 600
                    else
                      new TextArea:
                        text = fileContent
                        editable = false
                        prefRowCount = 20
                        prefColumnCount = 50
                        styleClass.add("fixed-width")
                  )
              alert.showAndWait()
          add(link, col, row)
        }
    else
      new Label("Directory does not exist")

    grid.add(new Label("Name:"), 0, 0)
    grid.add(new Label(name), 1, 0)
    grid.add(new Label("Version:"), 0, 1)
    grid.add(new Label(version), 1, 1)
    grid.add(new Label("Build Number:"), 0, 2)
    grid.add(new Label(buildNumber), 1, 2)
    grid.add(new Label("Major Version:"), 0, 3)
    grid.add(new Label(majorVersion), 1, 3)
    grid.add(new Label("Scala Version:"), 0, 4)
    grid.add(new Label(scalaVersion), 1, 4)
    grid.add(new Label("Data Version:"), 0, 5)
    grid.add(new Label(dataVersion), 1, 5)
    grid.add(new Label("Data Directory:"), 0, 6)
    grid.add(new Label(dataPath.toString), 1, 6)
    val logFilePath = dataPath / "fdswarm.log"
    val logFileLink = new Hyperlink(logFilePath.toString):
      onAction = _ =>
        try
          java.awt.Desktop.getDesktop.open(logFilePath.toIO)
        catch
          case _: Throwable => ()
    grid.add(new Label("Log File:"), 0, 7)
    grid.add(logFileLink, 1, 7)
    grid.add(new Label("Data Files:"), 0, 8)
    grid.add(dataFilesNode, 1, 8)
    grid.add(new Label("Node:"), 0, 9)
    grid.add(new Label(NodeIdentityManager.nodeIdentity.toString), 1, 9)
    grid.add(new Label("Transport:"), 0, 10)
    grid.add(new Label(transport.mode), 1, 10)

    val docsUrl = s"http://${NodeIdentityManager.nodeIdentity.hostPort}/docs"
    val docsLink = new Hyperlink(docsUrl):
      onAction = _ =>
        try
          java.awt.Desktop.getDesktop.browse(new java.net.URI(docsUrl))
        catch
          case _: Throwable => ()

    grid.add(new Label("API Docs:"), 0, 11)
    grid.add(docsLink, 1, 11)

    grid.add(new Label("Java Version:"), 0, 12)
    grid.add(new Label(sys.props("java.version")), 1, 12)
    grid.add(new Label("Java Home:"), 0, 13)
    grid.add(new Label(sys.props("java.home")), 1, 13)

    val javaDetailsButton = new Hyperlink("More Java Details"):
      onAction = _ =>
        val props = sys.props.toSeq.sortBy(_._1).filter { (k, v) =>
          (k.startsWith("java.") || k.contains("arch") || k.contains("os.") || k.contains("vendor") || k.contains("vm.")) &&
          k != "java.class.path"
        }
        val javaInfo = props.map { case (k, v) => s"$k: $v" }.mkString("\n")

        val groups = props.groupBy { (k, v) =>
          if (k.startsWith("java.")) "Java"
          else if (k.contains("os.")) "OS"
          else if (k.contains("vm.")) "VM"
          else if (k.contains("arch")) "Architecture"
          else if (k.contains("vendor")) "Vendor"
          else "Other"
        }.toSeq.sortBy(_._1)

        val container = new VBox { spacing = 20 }

        groups.foreach { case (groupName, groupProps) =>
          val groupLabel = new Label(groupName) {
            style = "-fx-font-size: 16px; -fx-font-weight: bold; -fx-underline: true;"
          }
          val table = new GridPane:
            hgap = 10
            vgap = 4
            padding = Insets(5, 10, 10, 10)

            groupProps.sortBy(_._1).zipWithIndex.foreach { case ((k, v), idx) =>
              val keyLabel = new Label(k + ":") {
                style = "-fx-font-weight: bold;"
                minWidth = scalafx.scene.layout.Region.USE_PREF_SIZE
              }
              val valueLabel = new Label(v) {
                wrapText = true
                maxWidth = Double.MaxValue
              }
              GridPane.setHgrow(valueLabel, Priority.Always)
              add(keyLabel, 0, idx)
              add(valueLabel, 1, idx)
            }
          container.children.addAll(groupLabel, table)
        }

        val contentScroll = new ScrollPane:
          content = container
          fitToWidth = true
          prefViewportHeight = 400
          prefViewportWidth = 700

        val alert = new Alert(AlertType.Information):
          initOwner(window)
          title = "Java & Architecture Details"
          headerText = "Detailed Java and System Information"
          val copyButton = new Button("Copy to Clipboard"):
            onAction = _ =>
              val content = new ClipboardContent()
              content.putString(javaInfo)
              Clipboard.systemClipboard.setContent(content)
          dialogPane().content = new VBox:
            spacing = 10
            children = Seq(
              copyButton,
              contentScroll
            )
        alert.showAndWait()

    grid.add(new Label("Java Details:"), 0, 14)
    grid.add(javaDetailsButton, 1, 14)

    val configDetailsButton = new Hyperlink("Show application.conf"):
      onAction = _ =>
        val configStr = config.root().render(com.typesafe.config.ConfigRenderOptions.defaults().setOriginComments(false).setComments(true).setFormatted(true).setJson(false))
        val alert = new Alert(AlertType.Information):
          initOwner(window)
          title = "application.conf"
          headerText = "Application Configuration"
          val copyButton = new Button("Copy to Clipboard"):
            onAction = _ =>
              val content = new ClipboardContent()
              content.putString(configStr)
              Clipboard.systemClipboard.setContent(content)
          dialogPane().content = new VBox:
            spacing = 10
            children = Seq(
              copyButton,
              new TextArea:
                text = configStr
                editable = false
                prefRowCount = 30
                prefColumnCount = 80
                styleClass.add("fixed-width")
            )
        alert.showAndWait()

    grid.add(new Label("Application Config:"), 0, 15)
    grid.add(configDetailsButton, 1, 15)

    val environmentVariablesButton = new Hyperlink("Show environment variables"):
      onAction = _ =>
        val envVars = sys.env.toSeq.sortBy(_._1)
        val envStr = envVars.map { case (k, v) => s"$k: $v" }.mkString("\n")

        val table = new GridPane:
          hgap = 10
          vgap = 4
          padding = Insets(5, 10, 10, 10)

        envVars.zipWithIndex.foreach { case ((k, v), idx) =>
          val keyLabel = new Label(k + ":") {
            style = "-fx-font-weight: bold;"
            minWidth = scalafx.scene.layout.Region.USE_PREF_SIZE
          }
          val valueLabel = new Label(v) {
            wrapText = true
            maxWidth = Double.MaxValue
          }
          GridPane.setHgrow(valueLabel, Priority.Always)
          table.add(keyLabel, 0, idx)
          table.add(valueLabel, 1, idx)
        }

        val contentScroll = new ScrollPane:
          content = table
          fitToWidth = true
          prefViewportHeight = 400
          prefViewportWidth = 800

        val alert = new Alert(AlertType.Information):
          initOwner(window)
          title = "Environment Variables"
          headerText = "Process Environment Variables"
          val copyButton = new Button("Copy to Clipboard"):
            onAction = _ =>
              val content = new ClipboardContent()
              content.putString(envStr)
              Clipboard.systemClipboard.setContent(content)
          dialogPane().content = new VBox:
            spacing = 10
            children = Seq(
              copyButton,
              contentScroll
            )
        alert.showAndWait()

    grid.add(new Label("Environment:"), 0, 16)
    grid.add(environmentVariablesButton, 1, 16)

    val groupAddr = if (config.hasPath("fdswarm.UDP.groupAddr")) config.getString("fdswarm.UDP.groupAddr") else "Not configured"
    grid.add(new Label("UDP Group Addr:"), 0, 17)
    grid.add(new Label(groupAddr), 1, 17)

    grid.add(new Label("UDP Instance ID:"), 0, 18)
    grid.add(new Label(NodeIdentityManager.nodeIdentity.instanceId), 1, 18)

    val startupInfoNode = startupInfo.info match {
      case None =>
        new Label("Not Used")
      case Some(sc) => 
        val button = new Hyperlink("View StartupConfig") {
          onAction = _ => {
            val jsonString = sc.asJson.spaces2
            
            val alert = new Alert(AlertType.Information) {
              initOwner(window)
              title = "StartupConfig"
              headerText = "Startup Configuration"
              
              val copyButton = new Button("Copy to Clipboard") {
                onAction = _ => {
                  val content = new ClipboardContent()
                  content.putString(jsonString)
                  Clipboard.systemClipboard.setContent(content)
                }
              }
              
              dialogPane().content = new VBox {
                spacing = 10
                children = Seq(
                  copyButton,
                  new ScrollPane {
                    content = JsonPrettyPrinter.toTable(jsonString)
                    prefViewportHeight = 400
                    prefViewportWidth = 600
                  }
                )
              }
            }
            alert.showAndWait()
          }
        }
        button
    }
    grid.add(new Label("StartupInfo:"), 0, 19)
    grid.add(startupInfoNode, 1, 19)

    val labels = grid.children.collect { case l: javafx.scene.control.Label => l }
    labels.foreach(_.getStyleClass.add("fixed-width"))

    val copyAllButton = new Button("Copy All to Clipboard"):
      onAction = _ =>
        val sb = new StringBuilder
        sb.append(s"Name: $name\n")
        sb.append(s"Version: $version\n")
        sb.append(s"Build Number: $buildNumber\n")
        sb.append(s"Major Version: $majorVersion\n")
        sb.append(s"Scala Version: $scalaVersion\n")
        sb.append(s"Data Version: $dataVersion\n")
        sb.append(s"Data Directory: $dataPath\n")
        sb.append(s"Log File: $logFilePath\n")
        sb.append(s"Host: ${NodeIdentityManager.nodeIdentity}\n")
        sb.append(s"Java Version: ${sys.props("java.version")}\n")
        sb.append(s"Java Home: ${sys.props("java.home")}\n")
        val groupAddr = if (config.hasPath("fdswarm.UDP.groupAddr")) config.getString("fdswarm.UDP.groupAddr") else "Not configured"
        sb.append(s"UDP Group Addr: $groupAddr\n")
        sb.append(s"UDP Instance ID: ${NodeIdentityManager.nodeIdentity.instanceId}\n")
        sb.append(s"StartupInfo: ${if (startupInfo.info.isEmpty) "Not Used" else "Used"}\n")
        val configStr = config.root().render(com.typesafe.config.ConfigRenderOptions.defaults().setOriginComments(false).setComments(true).setFormatted(true).setJson(false))
        sb.append(s"\n--- Application Config ---\n$configStr\n")
        val envStr = sys.env.toSeq.sortBy(_._1).map { case (k, v) => s"$k: $v" }.mkString("\n")
        sb.append(s"\n--- Environment Variables ---\n$envStr\n")
        val content = new ClipboardContent()
        content.putString(sb.toString())
        Clipboard.systemClipboard.setContent(content)

    val contentBox = new VBox {
      spacing = 10
      children = Seq(headerBox, copyAllButton, grid)
    }

    val aboutAlert = new Alert(AlertType.Information) {
      initOwner(window)
      title = "About FdSwarm"
      headerText = "fdswarm build information"
      dialogPane().content = contentBox
    }
    aboutAlert.showAndWait()
