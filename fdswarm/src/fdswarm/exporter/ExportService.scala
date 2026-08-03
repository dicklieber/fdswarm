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

import fdswarm.fx.contest.{ContestConfig, ContestConfigManager, ContestType}
import fdswarm.fx.station.StationStore
import fdswarm.io.FileHelper
import fdswarm.store.QsoStore
import jakarta.inject.{Inject, Singleton}

@Singleton
final class ExportService @Inject()(
                                     qsoStore: QsoStore,
                                     fileHelper: FileHelper,
                                     contestManager: ContestConfigManager,
                                     stationStore: StationStore,
                                     val adifExporter: AdifExporter,
                                     val dupeSheetExporter: DupeSheetExporter,
                                     cabrilloHeaderStore: CabrilloHeaderStore
                                   ):

  enum ExportFormat(val extension: String, val description: String):
    case CABRILLO extends ExportFormat("cbr", "Cabrillo Files (*.cbr)")
    case ADIF extends ExportFormat("adi", "ADIF Files (*.adi)")
    case DUP extends ExportFormat("dup", "Dupe Sheet Files (*.dup)")
    case JSON extends ExportFormat("json", "JSON Files (*.json)")
    case SUMMARY extends ExportFormat("txt", "Summary Files (*.txt)")
    case ZIP  extends ExportFormat("zip", "Zip Files (*.zip)")

    override def toString: String = description

  def executeExport(path: os.Path, format: ExportFormat): Unit =
    format match
      case ExportFormat.ADIF =>
        val qsos = qsoStore.all
        val adif = adifExporter.exportQsos(qsos)
        os.write.over(path, adif)
      case ExportFormat.DUP =>
        val qsos = qsoStore.all
        val station = stationStore.station.value
        val header = cabrilloHeaderStore.header.value
        val callUsed = if header.callsign.nonEmpty then header.callsign else station.operator.value
        val dupSheet = dupeSheetExporter.exportQsos(qsos, callUsed, header.stationClass, header.stationSection)
        os.write.over(path, dupSheet)
      case ExportFormat.CABRILLO =>
        val qsos = qsoStore.all
        val station = stationStore.station.value
        val value: ContestConfig = contestManager.contestConfigProperty.value
        val contest:ContestType = value.contestType
        val header = cabrilloHeaderStore.header.value
        val cabrillo = CabrilloExporter.exportQsos(qsos, station, contest, header)
        os.write.over(path, cabrillo)
      case ExportFormat.JSON =>
        val qsos = qsoStore.all
        val json = JsonExporter.exportQsos(qsos)
        os.write.over(path, json)
      case ExportFormat.SUMMARY =>
        val qsos = qsoStore.all
        val summary = SummaryExporter.exportQsos(qsos)
        os.write.over(path, summary)
      case ExportFormat.ZIP =>
        ZipExporter.zipDirectory(fileHelper.directory, path)
