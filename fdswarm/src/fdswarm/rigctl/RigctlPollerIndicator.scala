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

package fdswarm.rigctl

import fdswarm.fx.utils.BootstrapIcons
import scalafx.Includes.*
import scalafx.scene.Cursor
import scalafx.scene.control.{Label, Tooltip}
import scalafx.scene.paint.Color

/** Visual indicator for [[RigctldPoller]] activity.
 *
 *  - Successful poll: the `arrow-clockwise` icon rotates 90° each time.
 *  - Error: switches to a red `slash-circle-fill` icon with a tooltip showing
 *    the error description.
 *
 * @param rigctldPoller the poller to observe
 * @param onClick       optional action invoked when the user clicks the
 *                      indicator; when provided the cursor becomes a hand and
 *                      a "Click to open RigCtl Settings" tooltip is shown on
 *                      success.  Pass `None` for the non-clickable variant
 *                      (e.g. inside the RigCtl dialog itself).
 */
class RigctlPollerIndicator(
    rigctldPoller: RigctldPoller,
    onClick: Option[() => Unit] = None
) extends Label:

  private val successIcon = BootstrapIcons.svgPath("arrow-clockwise", 20, Color.DarkGreen)
  private val errorIcon   = BootstrapIcons.svgPath("slash-circle-fill", 20, Color.Red)
  private var rotationAngle = 0.0

  style   = "-fx-padding: 4;"
  graphic = successIcon
  showSuccessTooltip()

  // Rotate the arrow icon on every successful poll
  rigctldPoller.pollCount.onChange { (_, _, _) =>
    rotationAngle = (rotationAngle + 90.0) % 360.0
    successIcon.rotate = rotationAngle
  }

  // Switch icon when error state changes
  rigctldPoller.lastError.onChange { (_, _, newVal) =>
    newVal match
      case Some(msg) =>
        graphic = errorIcon
        delegate.setTooltip(new javafx.scene.control.Tooltip(msg))
      case None =>
        graphic = successIcon
        showSuccessTooltip()
  }

  onClick match
    case Some(action) =>
      cursor = Cursor.Hand
      onMouseClicked = _ => action()
    case None => ()

  private def showSuccessTooltip(): Unit =
    onClick match
      case Some(_) => delegate.setTooltip(new javafx.scene.control.Tooltip("Click to open RigCtl Settings"))
      case None    => delegate.setTooltip(null)
