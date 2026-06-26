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
import scala.util.Try

/** Parses an ADIF (.adi) file from an [[InputStream]] into a sequence of [[Qso]] objects.
  *
  * Record-level parsing is delegated to [[AdifRecordParser]]; see its scaladoc for the
  * full list of extracted fields and fallback behaviour.
  *
  * @param inputStream the ADIF file content
  * @param contestType contest type to embed; defaults to [[ContestType.ARRL]]
  */
class AdifParser(inputStream: InputStream, contestType: ContestType = ContestType.ARRL):

  private given qsoMetadata: QsoMetadata = QsoMetadata.adifImportQsoMetadata(contestType)

  /** Returns all successfully parsed [[Qso]] records from the ADIF file.
    * Records that cannot be parsed (e.g. missing required fields) are silently dropped.
    */
  def qsos: Seq[Try[Qso]] =
    val content = scala.io.Source.fromInputStream(inputStream, "UTF-8").mkString
    val eohIdx  = "(?i)<eoh>".r.findFirstMatchIn(content).map(_.end).getOrElse(0)
    val body: String = content.substring(eohIdx)
    splitRecords(body).map(AdifRecordParser().parseRecord(_))

  /** Splits the ADIF body on <EOR>/<eor> boundaries. */
  private def splitRecords(body: String): Seq[String] =
    body.split("(?i)<eor>").toSeq.map(_.trim).filter(_.nonEmpty)
