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

package fdswarm.exporter

import jakarta.inject.{Inject, Singleton}
import scala.jdk.CollectionConverters.*

@Singleton
final class ExportService @Inject()(
                                     exporters: java.util.Set[Exporter]
                                   ):

  def availableExporters: Seq[Exporter] = exporters.asScala.toSeq.sortBy(_.menu()._2)

  def executeExport(path: os.Path, exporter: Exporter): Unit =
    val fos = new java.io.FileOutputStream(path.toIO)
    try exporter.doExport(fos)
    finally fos.close()
