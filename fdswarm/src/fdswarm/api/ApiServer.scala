
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
import com.comcast.ip4s.*
import fdswarm.logging.LazyStructuredLogging
import fdswarm.util.NodeIdentityManager
import jakarta.inject.{Inject, Singleton}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.http4s.server.middleware.Logger as HttpLogger
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import java.util.Set
import scala.jdk.CollectionConverters.*

@Singleton
class ApiServer @Inject()(
                           nodeIdentityManager: NodeIdentityManager,
                           endpointsSet: Set[ApiEndpoints]
                         ) extends LazyStructuredLogging:

  def start(): IO[Unit] =
    val allEndpoints = endpointsSet.asScala.toList.flatMap { ae =>
      logger.debug(s"Adding endpoints from ${ae.getClass.getName}")
      ae.endpoints
    }

    val swaggerEndpoints =
      SwaggerInterpreter().fromServerEndpoints[IO](
        allEndpoints,
        s"${BuildInfo.appName} API",
        BuildInfo.version
      )

    val tapirRoutes = Http4sServerInterpreter[IO]().toRoutes(allEndpoints ++ swaggerEndpoints)
    
    val finalRoutes = Router(
      "/" -> tapirRoutes
    ).orNotFound

    val loggedRoutes =
      HttpLogger.httpApp[IO](logHeaders = true, logBody = false)(finalRoutes)

    val port = nodeIdentityManager.port
    
    logger.info(s"Starting API server on port $port")
    
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(Port.fromInt(port).getOrElse(port"8080"))
      .withHttpApp(loggedRoutes)
      .build
      .useForever
      .start // Run in background
      .void
