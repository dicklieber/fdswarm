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

import fdswarm.fx.contest.ContestType
import fdswarm.fx.station.StationConfig
import fdswarm.model.Qso

import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object CabrilloExporter:

  def exportQsos(qsos: Seq[Qso], station: StationConfig, contest: ContestType, header: CabrilloHeader): String =
    val sb = StringBuilder()
    // Header per Cabrillo v3
    sb.append("START-OF-LOG: 3.0\n")
    val callsign = if header.callsign.nonEmpty then header.callsign else station.operator.value
    sb.append(s"CALLSIGN: $callsign\n")
    val contestStr = if header.contest.nonEmpty then header.contest else mapContest(contest)
    sb.append(s"CONTEST: $contestStr\n")
    sb.append(s"CATEGORY-OPERATOR: ${header.categoryOperator}\n")
    sb.append(s"CATEGORY-ASSISTED: ${header.categoryAssisted}\n")
    sb.append(s"CATEGORY-BAND: ${header.categoryBand}\n")
    sb.append(s"CATEGORY-MODE: ${header.categoryMode}\n")
    header.categoryOperatorAge match
      case CategoryOperatorAge.NONE =>
      case other => sb.append(s"CATEGORY-OPERATOR-AGE: $other\n")
    sb.append(s"CATEGORY-POWER: ${header.categoryPower}\n")
    sb.append(s"CATEGORY-STATION: ${header.categoryStation}\n")
    sb.append(s"CATEGORY-TRANSMITTER: ${header.categoryTransmitter}\n")
    header.categoryOverlay match
      case CategoryOverlay.NONE =>
      case other => sb.append(s"CATEGORY-OVERLAY: $other\n")
    header.claimedScore.foreach(s => sb.append(s"CLAIMED-SCORE: $s\n"))
    if header.club.nonEmpty then sb.append(s"CLUB: ${header.club}\n")
    if header.operators.nonEmpty then sb.append(s"OPERATORS: ${header.operators}\n")
    if header.name.nonEmpty then sb.append(s"NAME: ${header.name}\n")
    if header.address.nonEmpty then sb.append(s"ADDRESS: ${header.address}\n")
    if header.addressCity.nonEmpty then sb.append(s"ADDRESS-CITY: ${header.addressCity}\n")
    if header.addressStateProvince.nonEmpty then sb.append(s"ADDRESS-STATE-PROVINCE: ${header.addressStateProvince}\n")
    if header.addressPostalCode.nonEmpty then sb.append(s"ADDRESS-POSTALCODE: ${header.addressPostalCode}\n")
    if header.addressCountry.nonEmpty then sb.append(s"ADDRESS-COUNTRY: ${header.addressCountry}\n")
    if header.soapbox.nonEmpty then sb.append(s"SOAPBOX: ${header.soapbox}\n")
    sb.append("CREATED-BY: FdSwarm\n")
    
    val sortedQsos = qsos.sortBy(_.stamp)
    sortedQsos.foreach { qso =>
      sb.append(toCabrilloRecord(qso, header))
      sb.append("\n")
    }
    
    sb.append("END-OF-LOG:\n")
    sb.toString()

  private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)
  private val timeFormatter = DateTimeFormatter.ofPattern("HHmm").withZone(ZoneOffset.UTC)

  def qsoLines(qsos: Seq[Qso], header: CabrilloHeader): Seq[String] =
    qsos.sortBy(_.stamp).map(toCabrilloRecord(_, header))

  private def mapContest(contest: ContestType): String =
    contest match
      case ContestType.WFD => "WFDA-CONTEST"
      case ContestType.ARRL => "ARRL-FIELD-DAY"
      case _ => throw new IllegalArgumentException(s"Unknown contest type: $contest")

  private def toCabrilloRecord(qso: Qso, header: CabrilloHeader): String =
    val freq = fdswarm.model.BandMode.bandToFreq(qso.bandMode.band)
    val mode = qso.bandMode.cabMode
    val date = dateFormatter.format(qso.stamp)
    val time = timeFormatter.format(qso.stamp)
    val myCall = if header.callsign.nonEmpty then header.callsign else qso.qsoMetadata.station.operator.value
    // Use configured station class/section from header, or empty if not set
    val mySect = header.stationSection
    val myCls = header.stationClass

    val hisCall = qso.callsign.value
    val hisClass = qso.exchange.fdClass.toString
    val hisSect = qso.exchange.sectionCode

    f"QSO: $freq%5s $mode%2s $date $time $myCall%-12s $myCls%-3s $mySect%-3s $hisCall%-12s $hisClass%-3s $hisSect%-3s"
