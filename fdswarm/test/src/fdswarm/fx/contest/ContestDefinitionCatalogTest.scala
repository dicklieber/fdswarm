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

import munit.FunSuite

class ContestDefinitionCatalogTest extends FunSuite:

  test("ContestCatalog"):
    val contestCatalog = ContestCatalog(new ContestTypes())
    val contests = contestCatalog.contests
    assertEquals(contests.size, 2)
    assertEquals(contestCatalog.getChars(ContestTypes.configured("ARRL")), "ABCDEF")
    assertEquals(contestCatalog.getChars(ContestTypes.configured("WFD")), "IOMDEF")
    assertEquals(contests.head.classChoices.head.description,
      "Club / group portable station (not at a home location)")

  test("ContestCatalog getChars handles unknown contest type"):
    val contestCatalog = ContestCatalog(new ContestTypes())
    assertEquals(
      contestCatalog.getChars(
        new ContestTypes()("WFD").copy(name = "Unknown")
      ),
      ""
    )

