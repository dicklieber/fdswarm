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

package fdswarm.fx

import _root_.io.circe.parser.decode
import _root_.io.circe.syntax.*
import _root_.io.circe.{Json, Printer}
import fdswarm.io.FileHelper
import jakarta.inject.{Inject, Singleton}
import scalafx.beans.property.{BooleanProperty, IntegerProperty, Property}

@Singleton
final class UserConfig @Inject()(fileHelper:FileHelper) :

  private val propertyList: List[Property[?, ?]] = List(
    new BooleanProperty(this, "usePhonetic", true),
    new BooleanProperty(this, "developerMode", false),
    new BooleanProperty(this, "useNextField", true),
    new IntegerProperty(this, "qsoListLines", 10),
    new IntegerProperty(this, "qsoListLines", 10),
    new BooleanProperty(this, "showWelcomeDialog", true)
  )

  private val properties: Map[String, Property[?, ?]] =
    propertyList.map(p => p.name -> p).toMap

  def getProperties: Map[String, Property[?, ?]] = properties

  def get[T](name: String): T =
    properties.get(name) match
      case Some(p) => p.value.asInstanceOf[T]
      case None => throw new NoSuchElementException(s"Property $name not found")

  def getProperty[T <: Property[?, ?]](name: String): T =
    properties.get(name) match
      case Some(p) => p.asInstanceOf[T]
      case None => throw new NoSuchElementException(s"Property $name not found")

  private val configFile = fileHelper.directory / "userConfig.json"

  def load(): Unit =
    if os.exists(configFile) then
      val jsonString = os.read(configFile)
      decode[Map[String, Json]](jsonString) match
        case Right(map) =>
          properties.foreach { case (name, prop) =>
            val jsonOpt = map.get(name)
            prop match
              case bp: BooleanProperty =>
                jsonOpt.flatMap(_.asBoolean).foreach(bp.value = _)
              case ip: IntegerProperty =>
                jsonOpt.flatMap(_.as[Int].toOption).foreach(ip.value = _)
              case _ => // ignore other types for now
          }
        case Left(err) =>
          // log error or ignore
          System.err.println(s"Error loading config: $err")

  def save(): Unit =
    val map = properties.map { case (name, prop) =>
      val jsonValue = prop match
        case bp: BooleanProperty => bp.value.asJson
        case ip: IntegerProperty => ip.value.asJson
        case _ => Json.fromString(prop.value.toString)
      name -> jsonValue
    }
    val printer = Printer.spaces2
//    os.makeDir.all(configFile / os.up)
    os.write.over(configFile, printer.print(map.asJson))

  // Initial load
  load()

  // Auto-save on change
  propertyList.foreach(_.onChange(save()))
