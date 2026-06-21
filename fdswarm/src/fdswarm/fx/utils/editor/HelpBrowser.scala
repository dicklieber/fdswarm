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

package fdswarm.fx.utils.editor

import fdswarm.util.NodeIdentityManager

import java.awt.Desktop
import java.net.URI

/**
 * Central helper for opening browser pages served by the local FdSwarm node.
 *
 * All URL construction delegates to [[NodeIdentityManager]] so the correct
 * host and port are always used, matching the pattern established in
 * `FdSwarmMenus` and `AboutMenuItem`.
 *
 * === URL resolution in [[open]] ===
 *
 *   - `"http(s)://..."` — used as-is (external URL)
 *   - `"/..."` — host+port is prepended (explicit server-absolute path)
 *   - anything else — treated as a path relative to `/fdswarmDocs/user/`
 *     (short form as stored in `@FieldHelp(url = "section/field")`)
 */
object HelpBrowser:

  private def hostPort: String = NodeIdentityManager.nodeIdentity.hostPort

  /**
   * Open a `@FieldHelp` link in the OS default browser.
   *
   * @param url short relative path (e.g. `"rigctl/port"`), a server-absolute
   *            path starting with `"/"`, or a full `http(s)://` URL.
   */
  def open(url: String): Unit =
    val fullUrl =
      if url.startsWith("http://") || url.startsWith("https://") then url
      else if url.startsWith("/") then s"http://$hostPort$url"
      else s"http://$hostPort/fdswarmDocs/user/$url"
    browse(fullUrl)

  /** Open the FdSwarm user documentation root (`/fdswarmDocs/`). */
  def openUserDocs(): Unit =
    browse(s"http://$hostPort/fdswarmDocs/")

  /** Open the Tapir / Swagger API documentation (`/docs`). */
  def openApiDocs(): Unit =
    browse(s"http://$hostPort/docs")

  private def browse(url: String): Unit =
    try Desktop.getDesktop.browse(new URI(url))
    catch case e: Throwable =>
      System.err.println(s"HelpBrowser: could not open $url: $e")
