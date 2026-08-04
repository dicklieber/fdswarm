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

import fdswarm.fx.contest.ContestConfigManager
import fdswarm.fx.station.StationStore
import fdswarm.model.{Mode, Qso}
import fdswarm.store.QsosSource
import jakarta.inject.{Inject, Singleton}

import java.io.OutputStream

@Singleton
class DupeSheetExporter @Inject()(
    qsosSource: QsosSource,
    stationStore: StationStore,
    contestManager: ContestConfigManager
) extends Exporter:

  override def menu(): (String, String) = ("dup", "Dupe Sheet Files (*.dup)")

  override def doExport(outputStream: OutputStream): Unit =
    val qsos = qsosSource.all
    val station = stationStore.station.value
    val config = contestManager.contestConfigProperty.value
    val callUsed = config.ourCallsign.toString
    val mySect = config.ourSection
    val myCls = s"${config.transmitters}${config.ourClass}"
    val dupSheet = exportQsos(qsos, callUsed, myCls, mySect)
    outputStream.write(dupSheet.getBytes("UTF-8"))

  def exportQsos(qsos: Seq[Qso], callUsed: String, stationClass: String, arrlSection: String): String =
    val sb = StringBuilder()

    // Header
    sb.append(s"Call Used: $callUsed  Class: $stationClass  ARRL Section: $arrlSection\n\n")
    sb.append("Dupe Sheet\n\n")

    // Group by Band and Mode
    val grouped = qsos.groupBy(q => (q.bandMode.band, q.bandMode.mode))

    // Sort groups: Frequency ascending (wavelength descending), then mode (CW, DIG, PH)
    val sortedGroups = grouped.toSeq.sortBy { case ((band, mode), _) =>
      (band.startFrequencyHz, mode.ordinal)
    }

    sortedGroups.foreach { case ((band, mode), groupQsos) =>
      val bandName = band.name.replace("m", "")
      val modeName = mode match
        case Mode.DIGI => "DIG"
        case other => other.toString

      sb.append(s"$bandName  $modeName\n")

      val callsigns = groupQsos.map(_.callsign.value).sorted
      callsigns.foreach { call =>
        sb.append(s"$call\n")
      }

      sb.append(s"\n$bandName  $modeName  Total Contacts = ${groupQsos.size}\n\n\n")
    }

    sb.toString()
