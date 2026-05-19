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
import jakarta.inject.Singleton
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint

import java.net.URLConnection
import scala.util.Using

/** Tapir endpoints for the FdSwarm user and developer documentation site. */
@Singleton
final class FdSwarmDocsEndpoints extends ApiEndpoints:

  private val docs: ServerEndpoint[Any, IO] =
    FdSwarmDocsEndpoints.docsDef
      .serverLogic[IO](segments =>
        IO.blocking(
          FdSwarmDocsEndpoints.docsResponse(segments)
        )
      )

  override def endpoints: List[ServerEndpoint[Any, IO]] = List(
    docs
  )

private object FdSwarmDocsEndpoints:
  private val resourceRoot = "FDSwarmDocs"

  private type DocsOutput = (StatusCode, String, Array[Byte])
  private type DocsErrorOutput = (StatusCode, String)

  private val docsBody =
    statusCode
      .and(header[String]("Content-Type"))
      .and(byteArrayBody)

  private val docsDef: PublicEndpoint[
    List[String],
    DocsErrorOutput,
    DocsOutput,
    Any
  ] =
    endpoint
      .get
      .in("fdswarmDocs")
      .in(paths)
      .errorOut(statusCode.and(stringBody))
      .out(docsBody)
      .description("FdSwarm documentation site")

  private def docsResponse(
      segments: List[String]
  ): Either[DocsErrorOutput, DocsOutput] =
    resourcePath(segments) match
      case None =>
        Left(
          StatusCode.BadRequest -> "Invalid documentation path"
        )
      case Some(path) =>
        readResource(path)
          .orElse(
            if path.contains('.') then None else readResource(s"$path/index.html")
          )
          .orElse(
            if segments.isEmpty then readResource(s"$resourceRoot/index.html") else None
          )
          .toRight(
            StatusCode.NotFound -> "FdSwarm documentation was not found in this jar"
          )

  private def resourcePath(
      segments: List[String]
  ): Option[String] =
    if segments.exists(segment =>
        segment.isBlank || segment == "." || segment == ".." || segment.contains("/")
      )
    then None
    else
      val relative =
        if segments.isEmpty then "index.html"
        else segments.mkString("/")
      Some(
        s"$resourceRoot/$relative"
      )

  private def readResource(
      path: String
  ): Option[DocsOutput] =
    resourceStream(path).flatMap(stream =>
      Using.resource(stream)(input =>
        Some(
          (
            StatusCode.Ok,
            contentType(path),
            input.readAllBytes()
          )
        )
      )
    )

  private def resourceStream(
      path: String
    ): Option[java.io.InputStream] =
    val classLoader = getClass.getClassLoader
    val contextClassLoader = Thread.currentThread().getContextClassLoader

    Option(classLoader.getResourceAsStream(path))
      .orElse(Option(contextClassLoader).flatMap(loader =>
        Option(loader.getResourceAsStream(path))
      ))

  private def contentType(
      path: String
  ): String =
    val guessed = Option(
      URLConnection.guessContentTypeFromName(path)
    ).getOrElse(
      "application/octet-stream"
    )

    if guessed.startsWith("text/") || guessed == "application/javascript" then
      s"$guessed; charset=utf-8"
    else
      guessed
