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

package fdswarm.importer

import com.typesafe.config.Config
import fdswarm.io.FileHelper
import fdswarm.logging.LazyStructuredLogging
import jakarta.inject.{Inject, Singleton}
import scalafx.beans.property.ObjectProperty

/** Manages [[WsjtxConfig]] persistence.
  *
  * On startup the config is loaded from `wsjtx.json` (via [[FileHelper]]).
  * If no file exists the defaults are taken from `fdswarm.wsjtx.*` in
  * `reference.conf`, so the operator can still control the defaults centrally.
  *
  * Call [[save]] to persist a changed config; the [[wsjtxConfig]] property is
  * updated immediately so other components that observe it stay in sync.
  */
@Singleton
class WsjtxConfigManager @Inject() (fileHelper: FileHelper, config: Config)
    extends LazyStructuredLogging():

  private val fileName = "wsjtx.json"

  /** Current WSJT-X listener configuration. */
  val wsjtxConfig: ObjectProperty[WsjtxConfig] =
    ObjectProperty(fileHelper.loadOrDefault[WsjtxConfig](fileName)(fromReferenceConf))

  /** Persists `cfg` and updates [[wsjtxConfig]]. */
  def save(cfg: WsjtxConfig): Unit =
    wsjtxConfig.value = cfg
    fileHelper.save(fileName, cfg)
    logger.info("WSJT-X config saved", "config" -> cfg.toString)

  private def fromReferenceConf: WsjtxConfig =
    WsjtxConfig(
      enabled        = config.getBoolean("fdswarm.wsjtx.enabled"),
      multicastGroup = config.getString("fdswarm.wsjtx.multicastGroup"),
      port           = config.getInt("fdswarm.wsjtx.port")
    )
