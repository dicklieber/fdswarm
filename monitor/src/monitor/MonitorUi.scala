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

package monitor

import com.google.inject.Inject
import scalafx.geometry.Insets
import scalafx.scene.Scene
import scalafx.scene.control.Button
import scalafx.scene.layout.{BorderPane, HBox}
import scalafx.stage.Stage

import java.awt.Desktop
import java.net.URI

final class MonitorUi @Inject() (
    nodeStore: NodeStore,
    nodeStatusView: NodeStatusView,
    logProcesser: LogProcesser,
    elasticsearchLogIndexer: ElasticsearchLogIndexer
):

  def start(primaryStage: Stage): Unit =
    primaryStage.title = "Monitor"
    primaryStage.scene = new Scene:
      root = new BorderPane:
        center = nodeStatusView.content(nodeStore.observableNodes)
        bottom = new HBox:
          padding = Insets(10)
          spacing = 10
          children = Seq(
            new Button("Clear"):
              onAction = _ =>
                nodeStore.clear()
                elasticsearchLogIndexer.deleteElasticsearchIndex()
            ,
            new Button("Open Kibana"):
              onAction = _ =>
                Desktop.getDesktop.browse(URI("http://localhost:5601/app/discover"))
          )

//  private def stop(): Unit =
//    logProcesser.stop()
//    udpPacketListener.stop()
