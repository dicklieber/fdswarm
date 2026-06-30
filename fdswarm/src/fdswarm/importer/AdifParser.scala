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
import java.io.InputStream

/** Carries the raw ADIF record string and the exception thrown when parsing it. */
case class QsoError(record: String, exception: Exception)

/** Parses an ADIF (.adi) file from an [[InputStream]] into individual [[Qso]] objects
  * via a callback, enabling streaming progress reporting.
  *
  * Record-level parsing is delegated to [[AdifRecordParser]]; see its scaladoc for the
  * full list of extracted fields and fallback behaviour.
  *
  * @param inputStream the ADIF file content
  * @param contestType contest type to embed; defaults to [[ContestType.ARRL]]
  */
class AdifParser(inputStream: InputStream, contestType: ContestType = ContestType.ARRL):

  private val rawRecords: Seq[String] =
    val content = scala.io.Source.fromInputStream(inputStream, "UTF-8").mkString
    val eohIdx  = "(?i)<eoh>".r.findFirstMatchIn(content).map(_.end).getOrElse(0)
    splitRecords(content.substring(eohIdx))

  /** Total number of ADIF records in the file; available before [[process]] is called. */
  val recordCount: Int = rawRecords.size

  /** All parsed records as a sequence of [[Either]] values; computed lazily on first access. */
  lazy val qsos: Seq[Either[QsoError, Qso]] =
    val buf = scala.collection.mutable.ArrayBuffer.empty[Either[QsoError, Qso]]
    process((_, result) => buf += result)
    buf.toSeq

  /** Parses each ADIF record and immediately invokes [[callback]] with the raw record
    * text and its result: [[Right]]`[`[[Qso]]`]` on success or
    * [[Left]]`[`[[QsoError]]`]` on failure.
    */
  def process(callback: (String, Either[QsoError, Qso]) => Unit): Unit =
    rawRecords.foreach { record =>
      val either = AdifRecordParser(() => QsoMetadata.adifImportQsoMetadata(contestType)).parseRecord(record).toEither.left.map { t =>
        val ex = t match
          case e: Exception => e
          case _ => RuntimeException(t.getMessage, t)
        QsoError(record, ex)
      }
      callback(record, either)
    }

  /** Splits the ADIF body on <EOR>/<eor> boundaries. */
  private def splitRecords(body: String): Seq[String] =
    body.split("(?i)<eor>").toSeq.map(_.trim).filter(_.nonEmpty)
