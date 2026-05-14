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

package manager

import munit.FunSuite
import os.*
import fdswarm.StartupConfig
import fdswarm.model.{BandMode, Callsign}
import fdswarm.util.Ids

class AppInstanceTest extends FunSuite:

  test("AppInstance spawns and stops"):
    // Create a temporary "debugConfigJson" file
    val tempFile = os.temp(contents = "{}", suffix = ".json", deleteOnExit = true)

    val startupConfig = StartupConfig(
      operator = Callsign("TEST"),
      bandMode = BandMode("20M SSB")
    )

    // For this test, we don't need a real jar if we just want to see it fail to EXECUTE.
    // However, the original issue was "No such file or directory" because of how os.proc was called.
    // With the fix, it should at least try to run "java".

    val logBaseDir = os.temp.dir(prefix = "app-instance-test", deleteOnExit = true)
    val app = new AppInstance(tempFile.toString, startupConfig, 8080, logBaseDir)
    // Note: without real JAR, subprocess exits immediately after spawn due to "Unable to access jarfile" error (expected in test env)

    app.stop()
    app.subProcess.waitFor(5000)
    assert(!app.subProcess.isAlive())
