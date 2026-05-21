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

package fdswarm.util
 
import fdswarm.fx.contest.ContestConfigManager
import jakarta.inject.{Inject, Singleton}

import java.time.*
import java.time.format.DateTimeFormatter
 
/**
 * Utility for generating standardized filenames for data exports and snapshots.
 *
 * The generated filename follows the pattern:
 * `${product}-${contest}-${dataVersion}_${timestamp}`
 *
 *   - `product`: Product name from `BuildInfo.productName` (e.g., "fdswarm").
 *   - `contest`: Current contest identifier from [[ContestConfigManager]] (e.g., "WFD").
 *   - `dataVersion`: Data schema version from `BuildInfo.dataVersion`.
 *   - `timestamp`: Canonical UTC timestamp in `yyyyMMdd'T'HHmmss'Z'` format.
 *
 * Example: `fdswarm-WFD-1.0.0_20260225T173742Z`
 */
@Singleton
class FilenameStamp @Inject():
 
  // ─────────────────────────────────────────────────────────────
  // Canonical UTC timestamp formatter (20260225T173742Z)
  // ─────────────────────────────────────────────────────────────
  private val UtcFormatter: DateTimeFormatter =
    DateTimeFormatter
      .ofPattern("yyyyMMdd'T'HHmmss'Z'")
      .withZone(ZoneOffset.UTC)

  private def format(instant: Instant): String =
    UtcFormatter.format(instant)
 

  def build(
             instant: Instant = Instant.now()
           ): String =
//    val contestType = contestManagerProvider.get().config.contestType
    val parts =
      List(com.organization.BuildInfo.productName,
        com.organization.BuildInfo.dataVersion)
        .map(sanitize)
 
    s"${parts.mkString("-")}_${format(instant)}"
 
  // ─────────────────────────────────────────────────────────────
  // Safety: strip dangerous characters
  // ─────────────────────────────────────────────────────────────
  private def sanitize(input: String): String =
    input.replaceAll("""[^A-Za-z0-9._-]""", "")
