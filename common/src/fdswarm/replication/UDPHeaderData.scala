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

package fdswarm.replication

import fdswarm.util.{Gzip, NodeIdentity}
import io.circe.Decoder

import java.nio.charset.StandardCharsets
import scala.util.{Failure, Success, Try}

case class UDPHeaderData(
  service: Service[?],
  nodeIdentity: NodeIdentity,
  payload: Array[Byte]
):

  def decodePayload[T](
    using Decoder[T]
  ): T =
    val decodedPayload = maybeDecompressGzip(payload)
    val jsonString = new String(decodedPayload, StandardCharsets.UTF_8)
    io.circe.parser.parse(jsonString) match
      case Left(error) => throw new RuntimeException(s"Failed to parse JSON: ${error.getMessage}", error)
      case Right(json) => json.as[T] match
        case Left(error) => throw new RuntimeException(s"Failed to decode JSON to T: ${error.getMessage}", error)
        case Right(value) => value

  def decodeFor[T](
    expectedService: Service[T]
  ): T =
    require(
      service == expectedService,
      s"Expected service $expectedService but got $service"
    )
    expectedService.decode(
      this
    )

  private def maybeDecompressGzip(
    input: Array[Byte]
  ): Array[Byte] =
    if isGzip(input) then
      Try(
        Gzip.decompress(
          input,
          UDPHeaderData.MaxDecompressedPayloadBytes
        )
      ) match
        case Success(bytes) => bytes
        case Failure(error) =>
          throw new RuntimeException(
            "Failed to decompress GZIP payload",
            error
          )
    else
      input

  private def isGzip(
    input: Array[Byte]
  ): Boolean =
    input.length >= 2 &&
      input(0) == 0x1f.toByte &&
      input(1) == 0x8b.toByte

object UDPHeaderData:
  val MaxDecompressedPayloadBytes: Int = 1024 * 1024
