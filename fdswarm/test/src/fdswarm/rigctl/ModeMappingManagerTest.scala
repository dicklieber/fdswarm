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

package fdswarm.rigctl

import com.typesafe.config.ConfigFactory
import fdswarm.io.FileHelper
import fdswarm.model.Mode
import munit.FunSuite

class ModeMappingManagerTest extends FunSuite:

  private val testConfigStr =
    """
      |fdswarm.modes {
      |  CW   = "CW*"
      |  DIGI = "RTTY PKT*"
      |  PH   = "*"
      |}
    """.stripMargin

  private def makeManager(): (ModeMappingManager, os.Path) =
    val dir = os.temp.dir(prefix = "mode-mapping-test", deleteOnExit = true)
    val fileHelper = new FileHelper:
      override val directory: os.Path = dir
    val config = ConfigFactory.parseString(testConfigStr)
    (new ModeMappingManager(fileHelper, config), dir)

  test("fromString maps CW prefix modes to CW"):
    val (mgr, dir) = makeManager()
    try
      assertEquals(mgr.fromString("CW"),  Mode.CW)
      assertEquals(mgr.fromString("CWL"), Mode.CW)
      assertEquals(mgr.fromString("CWR"), Mode.CW)
    finally
      os.remove.all(dir)

  test("fromString is case-insensitive"):
    val (mgr, dir) = makeManager()
    try
      assertEquals(mgr.fromString("cw"),   Mode.CW)
      assertEquals(mgr.fromString("rtty"), Mode.DIGI)
      assertEquals(mgr.fromString("Usb"),  Mode.PH)
    finally
      os.remove.all(dir)

  test("fromString maps RTTY to DIGI (exact token)"):
    val (mgr, dir) = makeManager()
    try
      assertEquals(mgr.fromString("RTTY"), Mode.DIGI)
    finally
      os.remove.all(dir)

  test("fromString maps PKT prefix modes to DIGI"):
    val (mgr, dir) = makeManager()
    try
      assertEquals(mgr.fromString("PKT"),   Mode.DIGI)
      assertEquals(mgr.fromString("PKTFM"), Mode.DIGI)
      assertEquals(mgr.fromString("PKTUSB"), Mode.DIGI)
    finally
      os.remove.all(dir)

  test("fromString falls back to PH for unrecognised modes"):
    val (mgr, dir) = makeManager()
    try
      assertEquals(mgr.fromString("USB"), Mode.PH)
      assertEquals(mgr.fromString("LSB"), Mode.PH)
      assertEquals(mgr.fromString("FM"),  Mode.PH)
      assertEquals(mgr.fromString("AM"),  Mode.PH)
    finally
      os.remove.all(dir)

  test("fromString handles empty and whitespace-only input"):
    val (mgr, dir) = makeManager()
    try
      assertEquals(mgr.fromString(""),   Mode.PH)
      assertEquals(mgr.fromString("  "), Mode.PH)
    finally
      os.remove.all(dir)

  test("save persists custom mappings and they survive reload"):
    val dir = os.temp.dir(prefix = "mode-mapping-persist-test", deleteOnExit = true)
    try
      val config = ConfigFactory.parseString(testConfigStr)

      val fileHelper = new FileHelper:
        override val directory: os.Path = dir

      val mgr1 = new ModeMappingManager(fileHelper, config)
      // Add FT8 and FT4 as DIGI tokens
      mgr1.save(Map(
        "CW"   -> "CW*",
        "DIGI" -> "RTTY PKT* FT8 FT4",
        "PH"   -> "*"
      ))

      // New instance should read the persisted file
      val mgr2 = new ModeMappingManager(fileHelper, config)
      assertEquals(mgr2.fromString("FT8"), Mode.DIGI)
      assertEquals(mgr2.fromString("FT4"), Mode.DIGI)
      assertEquals(mgr2.fromString("USB"), Mode.PH)
    finally
      os.remove.all(dir)

  test("orderedMappings covers all Mode values"):
    val (mgr, dir) = makeManager()
    try
      val ordered = mgr.orderedMappings
      assertEquals(ordered.map(_._1).toSet, Mode.values.toSet)
      assert(ordered.nonEmpty)
    finally
      os.remove.all(dir)

  test("orderedMappings returns the correct tokens string for each mode"):
    val (mgr, dir) = makeManager()
    try
      val byMode = mgr.orderedMappings.toMap
      assertEquals(byMode(Mode.CW),   "CW*")
      assertEquals(byMode(Mode.DIGI), "RTTY PKT*")
      assertEquals(byMode(Mode.PH),   "*")
    finally
      os.remove.all(dir)

  test("fromString with wildcard-only PH token matches everything unmatched"):
    val (mgr, dir) = makeManager()
    try
      // Anything that is not CW* or RTTY/PKT* should be PH
      val phInputs = Seq("SSB", "DSB", "AM", "FM", "ECSSUSB", "ECSSDSB", "SAM", "AMS")
      phInputs.foreach { s =>
        assertEquals(mgr.fromString(s), Mode.PH, s"Expected PH for '$s'")
      }
    finally
      os.remove.all(dir)
