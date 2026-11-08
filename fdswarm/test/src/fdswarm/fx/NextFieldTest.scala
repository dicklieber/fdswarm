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

import fdswarm.JavaFxTestKit
import fdswarm.support.TempDirFileHelperSuite
import scalafx.beans.property.BooleanProperty
import scalafx.scene.control.TextField
import javafx.scene.input.KeyEvent
import scalafx.scene.input.KeyCode

class NextFieldTest extends TempDirFileHelperSuite:

  test("NextField.toChar maps basic keys correctly") {
    assertEquals(NextField.toChar(KeyCode.Space), ' ')
    assertEquals(NextField.toChar(KeyCode.Enter), '\n')
    assertEquals(NextField.toChar(KeyCode.Tab), '\t')
    assertEquals(NextField.toChar(KeyCode.A), 'A')
    assertEquals(NextField.toChar(KeyCode.Z), 'Z')
    assertEquals(NextField.toChar(KeyCode.Digit0), '0')
    assertEquals(NextField.toChar(KeyCode.Digit9), '9')
  }

  test("NextField.toChar returns space for unknown keys") {
    assertEquals(NextField.toChar(KeyCode.Shift), ' ')
    assertEquals(NextField.toChar(KeyCode.F1), ' ')
  }

  test("blocked advancement consumes forward keys with automatic transitions enabled or disabled") {
    JavaFxTestKit.runOnFx {
      val config = new UserConfig(fileHelper)
      var blocked = true
      val field = new TextField with NextField:
        override def userConfig: UserConfig = config
        override def isValid(str: String): Boolean = true
        override def canAdvance: Boolean = !blocked
      field.text = "WA9ABC"
      var advances = 0
      field.onDoneFunction = _ => advances += 1

      for enabled <- Seq(true, false) do
        config.getProperty[BooleanProperty]("useNextField").value = enabled
        for key <- Seq(javafx.scene.input.KeyCode.ENTER, javafx.scene.input.KeyCode.TAB) do
          field.delegate.fireEvent(new KeyEvent(
            KeyEvent.KEY_PRESSED, "", "", key, false, false, false, false
          ))
        if enabled then
          for key <- Seq(javafx.scene.input.KeyCode.SPACE, javafx.scene.input.KeyCode.DIGIT1) do
            field.delegate.fireEvent(new KeyEvent(
              KeyEvent.KEY_PRESSED, "", "", key, false, false, false, false
            ))
      assertEquals(advances, 0)
      assertEquals(field.text.value, "WA9ABC")
      blocked = false
      config.getProperty[BooleanProperty]("useNextField").value = true
      field.delegate.fireEvent(new KeyEvent(
        KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.ENTER,
        false, false, false, false
      ))
      assertEquals(advances, 1)
    }
  }
