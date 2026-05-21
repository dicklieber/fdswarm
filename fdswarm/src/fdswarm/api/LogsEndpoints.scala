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

package fdswarm.api

import cats.effect.IO
import com.organization.BuildInfo
import fdswarm.io.FileHelper
import jakarta.inject.{Inject, Singleton}
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

/** Tapir endpoints for downloading the current application log. */
@Singleton
final class LogsEndpoints @Inject() (fileHelper: FileHelper) extends ApiEndpoints:

  private val log: ServerEndpoint[Any, IO] =
    LogsEndpoints.logDef
      .serverLogic[IO](logResponse)

  override def endpoints: List[ServerEndpoint[Any, IO]] = List(
    log
  )

  private def logResponse(
      fromByte: Option[Long]
  ): IO[Either[LogsEndpoints.LogErrorOutput, LogsEndpoints.LogOutput]] =
    IO.blocking {
      logFetchService.fetch(
        fromByte.getOrElse(
          0L
        )
      ) match
        case Right(result) =>
          Right(
            LogsEndpoints.output(
              statusCode =
                if result.bytes.isEmpty && result.from == result.size then
                  StatusCode.NoContent
                else
                  StatusCode.Ok,
              from = result.from,
              to = result.to,
              size = result.size,
              truncated = result.truncated,
              logId = result.logId,
              bytes = result.bytes
            )
          )
        case Left(error: LogFetchError.NegativeFromByte) =>
          Left(
            LogsEndpoints.errorOutput(
              statusCode = StatusCode.BadRequest,
              from = error.from,
              to = error.from,
              size = error.size,
              truncated = error.truncated,
              logId = error.logId
            )
          )
        case Left(error: LogFetchError.FromBeyondFileSize) =>
          Left(
            LogsEndpoints.errorOutput(
              statusCode = StatusCode.Conflict,
              from = error.from,
              to = error.from,
              size = error.size,
              truncated = error.truncated,
              logId = error.logId
            )
          )
    }

  private lazy val logFetchService: LogFetchService =
    FileLogFetchService(
      (fileHelper.directory / s"${BuildInfo.productName}.log").toNIO
    )

private object LogsEndpoints:
  type LogOutput = (StatusCode, Long, Long, Long, String, String, String, Array[Byte])
  type LogErrorOutput = (StatusCode, Long, Long, Long, String, String)

  private val metadataHeaders =
    header[Long]("X-Log-From")
      .and(header[Long]("X-Log-To"))
      .and(header[Long]("X-Log-Size"))
      .and(header[String]("X-Log-Truncated"))
      .and(header[String]("X-Log-Id"))

  private val logBody =
    statusCode
      .and(metadataHeaders)
      .and(header[String]("Content-Type"))
      .and(byteArrayBody)

  private val logDef: PublicEndpoint[
    Option[Long],
    LogErrorOutput,
    LogOutput,
    Any
  ] =
    endpoint
      .get
      .in("log")
      .in(query[Option[Long]]("fromByte"))
      .errorOut(statusCode.and(metadataHeaders))
      .out(logBody)
      .description(s"Fetch complete NDJSON log records from the current ${BuildInfo.productName}.log file")

  private def output(
      statusCode: StatusCode,
      from: Long,
      to: Long,
      size: Long,
      truncated: Boolean,
      logId: String,
      bytes: Array[Byte]
  ): LogOutput =
    (
      statusCode,
      from,
      to,
      size,
      truncated.toString,
      logId,
      contentType,
      bytes
    )

  private def errorOutput(
      statusCode: StatusCode,
      from: Long,
      to: Long,
      size: Long,
      truncated: Boolean,
      logId: String
  ): LogErrorOutput =
    (
      statusCode,
      from,
      to,
      size,
      truncated.toString,
      logId
    )

  private val contentType =
    "application/x-ndjson; charset=utf-8"
