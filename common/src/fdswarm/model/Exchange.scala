
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

package fdswarm.model

import scala.language.strictEquality
import io.circe.{Decoder, Encoder}
import sttp.tapir.Schema

/**
 * A field day class
 *
 * @param transmitters how many.
 * @param stationClass
 */
case class FdClass(transmitters: Int = 1,
                   classLetter: Char = 'I') derives CanEqual:
  override def toString: String = s"$transmitters${classLetter}"

object FdClass:
  given Conversion[String, FdClass] = FdClass.apply
  given Schema[FdClass] = Schema.string
  given Encoder[FdClass] = Encoder.encodeString.contramap(_.toString)
  given Decoder[FdClass] = Decoder.decodeString.map(FdClass.apply)

  private val regex = """(\d{1,2})([A-Z])""".r
  def apply(str:String):FdClass=
    str match
      case regex(sTransmitters, sLetter) =>
        FdClass(sTransmitters.toInt, sLetter.head)
      case _ => 
        throw new IllegalArgumentException(s"$str is not a valid FdClass. Must be in format <transmitters><classLetter>, e.g. 1H or 2A)")
    
  def apply(transmitters: Int, stationClass: StationClass): FdClass =
    FdClass(
      transmitters = transmitters,
      classLetter = stationClass.value)

/**
 * Echange class and section in common ham contest jargon.
 * @param fdClass
 * @param sectionCode
 */
case class Exchange(fdClass: FdClass = FdClass(),
                    sectionCode: String = "IL") derives CanEqual:

  /**
   *
   * @return compact form
   */
  override def toString: String = s"""$fdClass $sectionCode"""

object Exchange:
  given Schema[Exchange] = Schema.string
  given Encoder[Exchange] = Encoder.encodeString.contramap(_.toString)
  given Decoder[Exchange] = Decoder.decodeString.map(Exchange.apply)

  private val Parse = """(.*) (.*)""".r
  def apply(str: String): Exchange =
    str match
      case Parse(sClass, sSection) =>
        Exchange(FdClass(sClass), sSection)
      case _ =>
        throw new IllegalArgumentException(s"$str is not a valid Exchange. Must be in format <class> <section>, e.g. 1H IL")

