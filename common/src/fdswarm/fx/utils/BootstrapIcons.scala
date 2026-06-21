package fdswarm.fx.utils

import scalafx.scene.shape.SVGPath
import scalafx.scene.paint.Color
import scala.xml.XML

object BootstrapIcons:

  private lazy val sprite =
    XML.load(getClass.getResourceAsStream("/icons/bootstrap-icons.svg"))

  private lazy val iconMap: Map[String, String] =
    (sprite \\ "symbol").flatMap { sym =>
      val id = (sym \ "@id").text
      val paths = (sym \\ "path").map(_ \@ "d").filter(_.nonEmpty)
      if paths.isEmpty then None
      else
        // Each <path> element in the sprite is independent: its first M/m is
        // relative to the viewBox origin (0,0), not the last point of the
        // previous path.  Inserting "M0 0" before every path after the first
        // resets the current point so relative moveto commands land correctly.
        val combined = paths.head + paths.tail.map(" M0 0 " + _).mkString
        Some(id -> combined)
    }.toMap

  def svgPath(name: String, size: Double = 16, color: Color = Color.Black): SVGPath =
    val pathData =
      iconMap.getOrElse(name,
        throw new IllegalArgumentException(s"Bootstrap icon '$name' not found"))

    new SVGPath:
      content = pathData
      fill = color
      scaleX = size / 16.0
      scaleY = size / 16.0
