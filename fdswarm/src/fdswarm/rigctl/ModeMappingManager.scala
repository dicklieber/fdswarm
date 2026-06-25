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

import com.typesafe.config.Config
import fdswarm.io.FileHelper
import fdswarm.logging.LazyStructuredLogging
import fdswarm.model.Mode
import jakarta.inject.{Inject, Singleton}
import scalafx.beans.property.ObjectProperty

import scala.jdk.CollectionConverters.CollectionHasAsScala

/** Manages the mapping from rigctl mode strings to the application [[Mode]] enum.
  *
  * The default mappings are loaded from `fdswarm.modes` in `reference.conf`.
  * User edits are persisted to `mode-mappings.json` via [[FileHelper]] and
  * take precedence on subsequent startups.
  *
  * Each mode's value is a space-separated list of tokens.  A token ending
  * with `*` matches any string with that prefix; the bare `*` matches
  * everything.  Modes are checked in priority order: CW → DIGI → PH, so
  * the catch-all `*` assigned to PH acts as a true fallback.
  */
@Singleton
class ModeMappingManager @Inject()(fileHelper: FileHelper, config: Config)
    extends LazyStructuredLogging:

  private val fileName = "modeMappings.json"

  /** Priority order used when resolving a rigctl string to a [[Mode]]. */
  private val priorityOrder: Seq[Mode] = Seq(Mode.CW, Mode.DIGI, Mode.PH)

  /** Default mappings parsed from `fdswarm.modes` in `reference.conf`.
    *
    * The config section is a flat object, e.g.:
    * {{{
    *   modes {
    *     CW   = "CW*"
    *     DIGI = "RTTY PKT*"
    *     PH   = "*"
    *   }
    * }}}
    */
  private def defaultMappings: Map[String, String] =
    try
      val modesConfig = config.getConfig("fdswarm.modes")
      modesConfig.entrySet().asScala.map { entry =>
        entry.getKey -> entry.getValue.unwrapped().toString
      }.toMap
    catch
      case e: Exception =>
        logger.warn("Could not parse fdswarm.modes from config, using built-in defaults",
          "error" -> e.getMessage)
        Map(
          "CW"   -> "CW*",
          "DIGI" -> "RTTY PKT*",
          "PH"   -> "*"
        )

  /** Current raw mappings (Mode name → space-separated tokens string).
    *
    * Loaded from the persisted file on startup; falls back to [[defaultMappings]].
    */
  val rawMappings: ObjectProperty[Map[String, String]] =
    ObjectProperty(fileHelper.loadOrDefault[Map[String, String]](fileName)(defaultMappings))

  /** Persist new mappings and update [[rawMappings]]. */
  def save(newMappings: Map[String, String]): Unit =
    rawMappings.value = newMappings
    fileHelper.save(fileName, newMappings)

  /** Resolve a rigctl mode string to the application [[Mode]] enum.
    *
    * The input is uppercased and trimmed before matching.  Modes are tried in
    * priority order (CW, DIGI, PH); the first mode whose token list contains a
    * match wins.  Falls back to [[Mode.PH]] if nothing matches.
    */
  def fromString(rigctlMode: String): Mode =
    val upper = rigctlMode.trim.toUpperCase
    val m     = rawMappings.value
    priorityOrder.find { mode =>
      val tokens = m.getOrElse(mode.toString, "").split("\\s+").filter(_.nonEmpty)
      tokens.exists(token => matchesToken(upper, token))
    }.getOrElse(Mode.PH)

  /** Returns each [[Mode]] paired with its current tokens string, in enum-definition order.
    *
    * Used by [[ModeMappingDialog]] to populate the editor rows.
    */
  def orderedMappings: Seq[(Mode, String)] =
    val m = rawMappings.value
    Mode.values.toSeq.map(mode => mode -> m.getOrElse(mode.toString, ""))

  private def matchesToken(input: String, token: String): Boolean =
    if token == "*" then true
    else if token.endsWith("*") then input.startsWith(token.dropRight(1))
    else input == token
