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

package fdswarm.replication.status

import com.google.inject.name.Named
import fdswarm.fx.GridColumns
import jakarta.inject.{Inject, Singleton}
import scalafx.application.Platform
import scalafx.beans.binding.Bindings
import scalafx.scene.control.Label
import scalafx.scene.layout.BorderPane

import java.util.concurrent.atomic.AtomicLong

@Singleton
class NodeBandOpPane @Inject() (
    swarmData: SwarmData,
    @Named("fdswarm.nodeBandOpRefreshSeconds")
    nodeBandOpRefreshSeconds: Int):

  private val titleLabel = new Label()
  private val contentPane = new BorderPane:
    center = swarmData.buildGridPane(NodeDataField.bandOpFields)
  private val titleBinding = Bindings.createStringBinding(
    () =>
      val nodeCount = swarmData.size.value
      if nodeCount == 1 then "Swarm" else s"Swarm ($nodeCount nodes)",
    swarmData.size
  )
  titleLabel.text <== titleBinding
  val node = GridColumns.fieldSet(
    titleLabel,
    contentPane
  )
  private val refreshIntervalMillis = math.max(0L, nodeBandOpRefreshSeconds.toLong * 1000L)
  private val lastRefreshMillis = AtomicLong(0L)

  def refresh(): Unit = refreshInternal(force = true)

  private def rebuildContent(): Unit =
    contentPane.center = swarmData.buildGridPane(NodeDataField.bandOpFields)

  private def requestRebuildContent(): Unit =
    if Platform.isFxApplicationThread then rebuildContent()
    else Platform.runLater {
      rebuildContent()
    }

  private def refreshInternal(force: Boolean): Unit =
    val now = System.currentTimeMillis()
    if force then
      lastRefreshMillis.set(now)
      requestRebuildContent()
    else if markRefreshDue(now) then
      requestRebuildContent()

  private def markRefreshDue(now: Long): Boolean =
    if refreshIntervalMillis <= 0 then return true
    var retry = true
    while retry do
      val last = lastRefreshMillis.get()
      if now - last < refreshIntervalMillis then return false
      if lastRefreshMillis.compareAndSet(last, now) then return true
    false

  def refreshIfDue(): Unit = refreshInternal(force = false)
