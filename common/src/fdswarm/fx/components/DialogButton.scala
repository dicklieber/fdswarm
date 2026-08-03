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
 */

package fdswarm.fx.components

import fdswarm.fx.utils.BootstrapIcons
import scalafx.scene.control.{Button, Tooltip}
import scalafx.scene.paint.Color

/**
 * Bootstrap 5-inspired button variants.
 *
 * Each variant carries the CSS class name (defined in `app.css`) and the
 * icon colour appropriate for that background (white on dark, black on light).
 */
enum ButtonVariant(val cssClass: String, val iconColor: Color):
  case Primary   extends ButtonVariant("btn-primary",   Color.White)
  case Secondary extends ButtonVariant("btn-secondary", Color.White)
  case Danger    extends ButtonVariant("btn-danger",    Color.White)
  case Success   extends ButtonVariant("btn-success",   Color.White)
  case Help      extends ButtonVariant("btn-help",      Color.Black)
  case Warning   extends ButtonVariant("btn-warning",   Color.Black)

/**
 * Factory for Bootstrap 5-style labelled icon buttons.
 *
 * Styling is CSS-driven; the scene must have `app.css` applied via
 * `fdswarm.fx.utils.UiStyles` for the variant classes to take effect.
 *
 * Usage:
 * {{{
 * val save = DialogButton("Save", "floppy",         ButtonVariant.Primary,   "Save configuration")
 * val close = DialogButton("Close", "x-circle",     ButtonVariant.Secondary, "Close without saving")
 * val help  = DialogButton("Help",  "question-circle", ButtonVariant.Help,   "Open documentation")
 * }}}
 */
object DialogButton:

  /**
   * @param label    button text
   * @param icon     Bootstrap Icons name (e.g. `"floppy"`, `"x-circle"`)
   * @param variant  colour/style variant; defaults to [[ButtonVariant.Secondary]]
   * @param tip      tooltip text; omitted when empty
   * @param iconSize icon height in pixels (default 14)
   */
  def apply(
    label: String,
    icon: String,
    variant: ButtonVariant = ButtonVariant.Secondary,
    tip: String = "",
    iconSize: Double = 14
  ): Button =
    new Button(label):
      styleClass += variant.cssClass
      graphic = BootstrapIcons.svgPath(icon, iconSize, variant.iconColor)
      if tip.nonEmpty then tooltip = new Tooltip(tip)
