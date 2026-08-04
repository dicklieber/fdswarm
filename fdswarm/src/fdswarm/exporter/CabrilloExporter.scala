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
import fdswarm.model.Qso
import fdswarm.store.QsosSource
import jakarta.inject.{Inject, Singleton}

import java.io.OutputStream
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Singleton
class CabrilloExporter @Inject()(
    qsosSource: QsosSource,
    contestManager: ContestConfigManager
) extends Exporter:

  override def menu(): (String, String) = ("cbr", "Cabrillo Files (*.cbr)")

  override def doExport(outputStream: OutputStream): Unit =
    val qsos = qsosSource.all
    val config: ContestConfig = contestManager.contestConfigProperty.value
    val cabrillo = exportQsos(qsos, config)
    outputStream.write(cabrillo.getBytes("UTF-8"))

  def exportQsos(qsos: Seq[Qso], config: ContestConfig): String =
    val sb = StringBuilder()
    // Header per Cabrillo v3
    sb.append("START-OF-LOG: 3.0\n")
    val callsign = config.ourCallsign.toString
    sb.append(s"CALLSIGN: $callsign\n")
    val contestStr = config.contestType.cabrilloContestName
    sb.append(s"CONTEST: $contestStr\n")
    if config.club.nonEmpty then sb.append(s"CLUB: ${config.club}\n")
    val location = if config.location.nonEmpty then config.location else config.ourSection
    sb.append(s"LOCATION: $location\n")
    
    val sortedQsos = qsos.sortBy(_.stamp)
    sortedQsos.foreach { qso =>
      sb.append(toCabrilloRecord(qso, config))
      sb.append("\n")
    }
    
    sb.append("END-OF-LOG:\n")
    sb.toString()

  private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)
  private val timeFormatter = DateTimeFormatter.ofPattern("HHmm").withZone(ZoneOffset.UTC)

  def qsoLines(qsos: Seq[Qso], config: ContestConfig): Seq[String] =
    qsos.sortBy(_.stamp).map(toCabrilloRecord(_, config))


  private def toCabrilloRecord(qso: Qso, config: ContestConfig): String =
    val freq = fdswarm.model.BandMode.bandToFreq(qso.bandMode.band)
    val mode = qso.bandMode.cabMode
    val date = dateFormatter.format(qso.stamp)
    val time = timeFormatter.format(qso.stamp)
    val myCall = config.ourCallsign.toString
    // Use configured station class/section from ContestConfig
    val mySect = config.ourSection
    val myCls = s"${config.transmitters}${config.ourClass}"

    val hisCall = qso.callsign.value
    val hisClass = qso.exchange.fdClass.toString
    val hisSect = qso.exchange.sectionCode

    f"QSO: $freq%5s $mode%2s $date $time $myCall%-12s $myCls%-3s $mySect%-3s $hisCall%-12s $hisClass%-3s $hisSect%-3s"
