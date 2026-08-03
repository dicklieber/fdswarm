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

package fdswarm.exporter

import fdswarm.model.{Band, Mode, Qso}

object SummaryExporter:

  def exportQsos(qsos: Seq[Qso]): String =
    val sb = StringBuilder()
    sb.append("Total Contacts by Band and Mode:\n\n")
    sb.append("Band       CW   Power     Dig   Power   Phone   Power   Total       %\n")
    sb.append("----       --   -----   -----   -----   -----   -----   -----     ---\n")

    val groupedByBand = qsos.groupBy(_.bandMode.band)
    val sortedBands = groupedByBand.keys.toSeq.sortBy(_.startFrequencyHz)
    val totalQsosCount = qsos.size

    sortedBands.foreach { band =>
      val bandQsos = groupedByBand(band)
      val cwCount = bandQsos.count(_.bandMode.mode == Mode.CW)
      val digiCount = bandQsos.count(_.bandMode.mode == Mode.DIGI)
      val phoneCount = bandQsos.count(_.bandMode.mode == Mode.PH)
      val bandTotal = bandQsos.size
      val percentage = if (totalQsosCount > 0) Math.round((bandTotal.toDouble / totalQsosCount) * 100).toInt else 0

      val bandName = formatBand(band)
      
      // Formatting based on the analysis:
      // Band (4) + 7 spaces + CW (2) + 3 spaces + Power (5) + 3 spaces + Dig (5) + 3 spaces + Power (5) + 3 spaces + Phone (5) + 3 spaces + Power (5) + 3 spaces + Total (5) + 5 spaces + % (3)
      sb.append(f"$bandName%4s")
      sb.append(" " * 7)
      sb.append(f"$cwCount%2d")
      sb.append(" " * 3)
      sb.append(" " * 5) // Power for CW
      sb.append(" " * 3)
      sb.append(f"$digiCount%5d")
      sb.append(" " * 3)
      sb.append(" " * 5) // Power for Dig
      sb.append(" " * 3)
      sb.append(f"$phoneCount%5d")
      sb.append(" " * 3)
      sb.append(" " * 5) // Power for Phone
      sb.append(" " * 3)
      sb.append(f"$bandTotal%5d")
      sb.append(" " * 5)
      sb.append(f"$percentage%3d")
      sb.append("\n")
    }

    // Footer
    val totalCw = qsos.count(_.bandMode.mode == Mode.CW)
    val totalDigi = qsos.count(_.bandMode.mode == Mode.DIGI)
    val totalPhone = qsos.count(_.bandMode.mode == Mode.PH)
    val grandTotal = qsos.size

    sb.append("           --   -----   -----   -----   -----   -----   -----     ---\n")
    sb.append(f"Total      $totalCw%2d")
    sb.append(" " * 3)
    sb.append(" " * 5) // Total Power for CW
    sb.append(" " * 3)
    sb.append(f"$totalDigi%5d")
    sb.append(" " * 3)
    sb.append(" " * 5) // Total Power for Dig
    sb.append(" " * 3)
    sb.append(f"$totalPhone%5d")
    sb.append(" " * 3)
    sb.append(" " * 5) // Total Power for Phone
    sb.append(" " * 3)
    sb.append(f"$grandTotal%5d")
    sb.append(" " * 5)
    sb.append(f"${100}%3d")
    sb.append("\n")

    sb.toString()

  private def formatBand(band: Band): String =
    if (band == Band.Band_6cm) "6cm"
    else band.name.replace("cm", "").replace("m", "")
