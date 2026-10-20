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

package fdswarm.fx.contest

import com.typesafe.config.ConfigFactory
import munit.FunSuite

class ContestDefinitionCatalogTest extends FunSuite:

  test("ContestCatalog"):
    val config = ConfigFactory.load()
    val contestCatalog = ContestCatalog(config)
    val contests = contestCatalog.contests
    assertEquals(contests.size, 2)

  test("ContestCatalog getChars handles unknown contest type"):
    val config = ConfigFactory.load()
    val contestCatalog = ContestCatalog(config)
    assertEquals(
      contestCatalog.getChars(
        new ContestTypes(config)("WFD").copy(name = "Unknown")
      ),
      ""
    )

