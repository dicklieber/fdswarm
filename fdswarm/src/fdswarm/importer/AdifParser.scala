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

import fdswarm.fx.contest.ContestType
import fdswarm.model.*
import fdswarm.util.NodeIdentity

import java.io.InputStream

/** Parses an ADIF (.adi) file from an [[InputStream]] into a sequence of [[Qso]] objects.
  *
  * Record-level parsing is delegated to [[AdifRecordParser]]; see its scaladoc for the
  * full list of extracted fields and fallback behaviour.
  *
  * @param inputStream the ADIF file content
  * @param node        node identity to embed in imported QSOs; defaults to [[NodeIdentity.mockNodeIdentity]]
  * @param contest     contest type to embed; defaults to [[ContestType.ARRL]]
  */
class AdifParser(
    inputStream: InputStream,
    node: NodeIdentity = NodeIdentity.mockNodeIdentity,
    contest: ContestType = ContestType.ARRL):

  private val recordParser = AdifRecordParser(node, contest)

  /** Returns all successfully parsed [[Qso]] records from the ADIF file.
    * Records that cannot be parsed (e.g. missing required fields) are silently dropped.
    */
  def qsos: Seq[Qso] =
    val content = scala.io.Source.fromInputStream(inputStream, "UTF-8").mkString
    val eohIdx  = "(?i)<eoh>".r.findFirstMatchIn(content).map(_.end).getOrElse(0)
    val body    = content.substring(eohIdx)
    splitRecords(body).flatMap(recordParser.parseRecord)

  /** Splits the ADIF body on <EOR>/<eor> boundaries. */
  private def splitRecords(body: String): Seq[String] =
    body.split("(?i)<eor>").toSeq.map(_.trim).filter(_.nonEmpty)
