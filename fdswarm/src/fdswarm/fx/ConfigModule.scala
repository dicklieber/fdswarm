
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

import com.google.inject.AbstractModule
import com.google.inject.name.Names
import com.typesafe.config.{Config, ConfigFactory}
import fdswarm.api.ApiEndpoints
import fdswarm.io.FileHelper
import fdswarm.logging.LazyStructuredLogging
import fdswarm.replication.{BroadcastTransport, Transport}
import fdswarm.{AutoBind, StartupInfo}
import net.codingwell.scalaguice.ScalaModule

import java.time.Duration
import scala.jdk.CollectionConverters.CollectionHasAsScala


class ConfigModule(rawArgs: Array[String]) extends AbstractModule with ScalaModule with LazyStructuredLogging:

  override def configure(): Unit =
    val fileHelper = new FileHelper()
    val loggingManager = new fdswarm.util.LoggingManager(fileHelper)
    bind[fdswarm.util.LoggingManager].toInstance(loggingManager)
    bind[FileHelper].toInstance(fileHelper)

    val startupInfo = new StartupInfo(rawArgs)
    bind[StartupInfo].toInstance(startupInfo)
    fdswarm.util.LoggingConfigurator.addFileAppender(fileHelper)
    loggingManager.applyInitialConfig()

    bind[fdswarm.util.NodeIdentityManager].asEagerSingleton()
    bind[fdswarm.replication.StatusProcessor].asEagerSingleton()

    val pkgs = Seq("fdswarm.api", "fdswarm.grafana")

    // unnamed set (inject with java.util.Set[ApiEndpoints])
    AutoBind.bindAllImplementationsOf[ApiEndpoints](
      binder = binder(),
      packagesOnly = pkgs,
      named = None,
      asSingleton = true
    )

    val primaryConfigFromFile = ConfigFactory.parseFile((os.pwd / "config" / "sarasec.conf").toIO)
    val defaultConfigFromFile: Config = ConfigFactory.load()
    val fullConfig: Config = primaryConfigFromFile.withFallback(defaultConfigFromFile)
//    bind[StatusBroadcastService].asEagerSingleton()
//    bind[NodeStatusDispatcher].asEagerSingleton()
//    bind[MulticastTransport].asEagerSingleton()
//    bind[BroadcastTransport].asEagerSingleton()

    val transportType = if fullConfig.hasPath("fdswarm.transportType") then fullConfig.getString("fdswarm.transportType") else "Multicast"
    if (transportType.equalsIgnoreCase("Broadcast")) 
      bind[Transport].to[BroadcastTransport].asEagerSingleton()
    else 
      throw new Exception("Transport type not supported: " + transportType)
//      bind[Transport].to[MulticastTransport].asEagerSingleton()

//    bind[QsoStore].asEagerSingleton()
//    bind[ContestStartManager].asEagerSingleton()
//    bind[ElasticShipper].asEagerSingleton()
//

    val entries = fullConfig.entrySet().asScala.toSeq
    for (entry <- entries) {
      val key = entry.getKey
      val value = fullConfig.getAnyRef(key)
      logger.trace(s"Config entry: $key = $value type: ${value.getClass}")
      scala.util.Try(
        fullConfig.getDuration(
          key
        )
      ).toOption.foreach(duration =>
        bind[Duration]
          .annotatedWith(Names.named(key))
          .toInstance(duration)
      )
      // Determine type and bind accordingly
      value match {
        case s: String =>
          bind[String]
            .annotatedWith(Names.named(key))
            .toInstance(s)

        case i: Integer =>
          bind[Int]
            .annotatedWith(Names.named(key))
            .toInstance(i.intValue)

        case l: java.lang.Long =>
          bind[Long]
            .annotatedWith(Names.named(key))
            .toInstance(l)

        case d: java.lang.Double =>
          bind[Double]
            .annotatedWith(Names.named(key))
            .toInstance(d)

        case b: java.lang.Boolean =>
          bind[Boolean]
            .annotatedWith(Names.named(key))
            .toInstance(b)

        case _ =>
        // Optionally log or ignore unsupported types
      }
    }

    bind[Config].toInstance(fullConfig)

object ConfigModule:
  given Conversion[String, os.Path] = (in: String) => os.Path(in)
