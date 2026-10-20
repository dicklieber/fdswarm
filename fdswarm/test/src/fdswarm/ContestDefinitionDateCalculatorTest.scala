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

package fdswarm

import munit.FunSuite

class ContestDefinitionDateCalculatorTest extends FunSuite:

  test("WFD fourth full weekend Jan 2026"):
    val dates = ContestDateCalculator.wfd(2026)
    assertEquals(dates.startUtc.toLocalDate, java.time.LocalDate.of(2026, 1, 24))
    assertEquals(dates.endUtc.toLocalDate, java.time.LocalDate.of(2026, 1, 25))
