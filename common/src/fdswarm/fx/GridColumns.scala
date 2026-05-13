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

import scalafx.scene.Node
import scalafx.scene.control.Label
import scalafx.scene.layout.GridPane

import scalafx.scene.layout.*
import scalafx.scene.control.Label
import scalafx.geometry.Insets

object GridColumns:

  /**
   * Create a GridPane from a sequence of `Node`s arranged by columns first, then rows.
   *
   * Layout strategy:
   * - Items are filled left-to-right (column-wise) with a fixed number of columns `nCols`.
   * - When a row reaches `nCols`, a new row is started.
   *
   * For example, with `nCols = 3` and 8 items, cells are placed at:
   * (row,col): (0,0) (0,1) (0,2) (1,0) (1,1) (1,2) (2,0) (2,1)
   */
  def toGrid(items: Seq[Node], nCols: Int): GridPane =
    require(nCols > 0, s"nCols must be > 0, but was $nCols")
    val grid = new GridPane():
      hgap = 2
      vgap = 2
    
    val cc = new ColumnConstraints() {
      hgrow = Priority.Never
    }
    grid.columnConstraints = (0 until nCols).map(_ => cc)

    items.zipWithIndex.foreach { case (item, idx) =>
      val row = idx / nCols
      val col = idx % nCols
      grid.add(item, col, row)
    }
    grid

  def fieldSet(title: String, content: Node): StackPane =
    fieldSet(new Label(title), content)

  def fieldSet(titleLabel: Node, content: Node): StackPane =
    new StackPane:
      children = Seq(
        new BorderPane:
          padding = Insets(20, 10, 10, 10)
          center = content
          style =
            """-fx-border-color: -fx-box-border;
               -fx-border-radius: 4;
               -fx-border-width: 1;"""
        ,
        titleLabel match {
          case l: Label =>
            l.style = l.style.value +
              """-fx-background-color: -fx-background;
                 -fx-padding: 0 6 0 6;"""
            l
          case n => n
        }
      )
      titleLabel.translateX = 10
      titleLabel.translateY = -8
      StackPane.setAlignment(titleLabel, scalafx.geometry.Pos.TopLeft)
