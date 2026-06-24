package fdswarm.fx.utils

import scalafx.scene.control.Button
import scalafx.scene.paint.Color

object IconButton:

  def apply(
      name: String,
      size: Double = 16,
      tooltipText: String = "",
      color: Color = Color.Black
  ): Button =

    val icon = BootstrapIcons.svgPath(name, size, color)

    val btn = new Button:
      graphic = icon
      style =
        """
          -fx-background-color: white;
          -fx-border-color: transparent;
          -fx-padding: 6;
        """

    if tooltipText.nonEmpty then
      btn.tooltip = tooltipText

    btn.onMouseEntered = _ =>
      btn.style =
        """
          -fx-background-color: rgba(0,0,0,0.08);
          -fx-border-color: transparent;
          -fx-padding: 6;
        """

    btn.onMouseExited = _ =>
      btn.style =
        """
          -fx-background-color: transparent;
          -fx-border-color: transparent;
          -fx-padding: 6;
        """

    btn

  // --- Standard named buttons ---

  /** Close a dialog or panel */
  def close(tooltip: String = "Close"): Button =
    apply("x-lg", 16, tooltip)

  /** Cancel / dismiss without saving */
  def cancel(tooltip: String = "Cancel"): Button =
    apply("x-circle", 16, tooltip)

  /** Save / confirm changes */
  def save(tooltip: String = "Save"): Button =
    apply("floppy2", 20, tooltip)

  /** Open a configuration dialog */
  def config(tooltip: String = "Configure"): Button =
    apply("sliders2-vertical", 20, tooltip)

  /** Delete an item (red to signal destructive action) */
  def delete(tooltip: String = "Delete"): Button =
    apply("trash3-fill", 20, tooltip, Color.Red)

  /** Reset / clear filters or fields (red to signal clearing action) */
  def reset(tooltip: String = "Reset"): Button =
    apply("x-octagon", 20, tooltip, Color.Red)

  /** Edit an item */
  def edit(tooltip: String = "Edit"): Button =
    apply("pencil", 16, tooltip)

  /** Search / pick from a list */
  def search(tooltip: String = "Search"): Button =
    apply("search", 20, tooltip)

  /** Sync / replicate to other nodes */
  def sync(tooltip: String = "Sync"): Button =
    apply("repeat", 20, tooltip)

  /** Warning / alert indicator */
  def warning(tooltip: String = "Warning"): Button =
    apply("exclamation-triangle", 20, tooltip, Color.Red)

  /** Open settings / gear menu */
  def settings(tooltip: String = "Settings"): Button =
    apply("gear", 20, tooltip)
