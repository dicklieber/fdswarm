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

import fdswarm.io.FileHelper
import jakarta.inject.{Inject, Singleton}
import os.Path

import java.io.{BufferedInputStream, FileInputStream, OutputStream}
import java.util.zip.{ZipEntry, ZipOutputStream}

@Singleton
class ZipExporter @Inject()(fileHelper: FileHelper) extends Exporter:

  override def menu(): (String, String) = ("zip", "Zip Files (*.zip)")

  override def doExport(outputStream: OutputStream): Unit =
    zipDirectoryToStream(fileHelper.directory, outputStream)

  def zipDirectoryToStream(sourceDirectory: Path, outputStream: OutputStream): Unit =
    val out = new ZipOutputStream(outputStream)
    try
      val files = os.list(sourceDirectory)
      files.foreach { path =>
        if os.isFile(path) then
          val entry = new ZipEntry(path.last)
          out.putNextEntry(entry)
          val in = new BufferedInputStream(new FileInputStream(path.toIO))
          try
            val buffer = new Array[Byte](4096)
            var n = 0
            while { n = in.read(buffer); n > 0 } do
              out.write(buffer, 0, n)
          finally
            in.close()
          out.closeEntry()
      }
    finally
      out.finish()

  def zipDirectory(sourceDirectory: Path, zipFile: Path): Unit =
    val fos = new java.io.FileOutputStream(zipFile.toIO)
    try zipDirectoryToStream(sourceDirectory, fos)
    finally fos.close()
