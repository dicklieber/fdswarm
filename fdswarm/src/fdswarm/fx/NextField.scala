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


/*
 * Copyright (C) 2021  Dick Lieber, WA9NNN
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

import _root_.scalafx.beans.property.BooleanProperty
import _root_.scalafx.scene.control.TextInputControl
import _root_.scalafx.scene.input.KeyCode
import fdswarm.fx.InputHelper.forceCaps
import scalafx.Includes.*
/**
 * Most of the common logic for any qso input field.
 */
trait NextField extends TextInputControl with WithDisposition:
  forceCaps(this)
  styleClass += "qsoField"
  sad()

  def userConfig: UserConfig

  def isValid(str: String): Boolean

  /** Keys that will trigger transition to the next field if the current field is valid.
    */
  def isTransitionKey(key: KeyCode): Boolean =
    key.isDigitKey || key == KeyCode.Space || key == KeyCode.Enter || key == KeyCode.Tab

  /** Whether forward traversal is currently allowed, independently of syntax validation. */
  def canAdvance: Boolean = true

  var onDoneFunction: String => Unit =
    (_) => {}

  delegate.addEventFilter(
    javafx.scene.input.KeyEvent.KEY_PRESSED,
    (event: javafx.scene.input.KeyEvent) => {
      val key: KeyCode = event.getCode
      val isBackwardTab = key == KeyCode.Tab && event.isShiftDown
      val isFieldValid = isValid(text.value)
      val useNextField = userConfig.get[Boolean]("useNextField")

      if (isBackwardTab) {
        // Preserve normal reverse focus traversal.
      } else if (!canAdvance && (key == KeyCode.Tab || key == KeyCode.Enter ||
          (isFieldValid && isTransitionKey(key) && useNextField))) {
        event.consume()
      } else if (isFieldValid && isTransitionKey(key) && useNextField) {
        event.consume()
        val str: String =
          if (key.isDigitKey || key.isLetterKey) NextField.toChar(key).toString else ""
        onDoneFunction(str)
      } else if (key == javafx.scene.input.KeyCode.ENTER) {
        event.consume()
        onDoneFunction("")
      }
    }
  )

  delegate.addEventFilter(
    javafx.scene.input.KeyEvent.KEY_TYPED,
    (event: javafx.scene.input.KeyEvent) => {
      // We only want to consume KEY_TYPED if it's one of the transition keys
      // which was already handled in KEY_PRESSED filter.
      // However, KEY_TYPED doesn't have a KeyCode, only the character.
      // In JavaFX, for most keys, KEY_PRESSED is followed by KEY_TYPED.

      // If the field is valid, and the character being typed is one of our transition characters,
      // we should consume it because we handled the transition in KEY_PRESSED.
      val char = event.getCharacter
      val useNextField = userConfig.get[Boolean]("useNextField")
      if (useNextField && char != null && char.nonEmpty) {
        val isFieldValid = isValid(text.value)

        // We need to know if this character would correspond to a transition key.
        // Since we don't have KeyCode here, we have to approximate it.
        val isTransitionChar =
          char == " " || char == "\r" || char == "\n" || char == "\t" || char.head.isDigit || char.head.isLetter

        if (isFieldValid && isTransitionChar) {
          // We only consume if the character is ACTUALLY a transition key for this field.
          // We can't easily map back from char to KeyCode reliably for all keys,
          // but for letters and digits, we can check if they are considered transition keys.
          val isActualTransition = if (char.head.isLetter) {
            isTransitionKey(javafx.scene.input.KeyCode.A) // Proxy for any value key
          } else if (char.head.isDigit) {
            isTransitionKey(javafx.scene.input.KeyCode.DIGIT0) // Proxy for any digit key
          } else {
            char == " " || char == "\r" || char == "\n" || char == "\t"
          }

          if (isActualTransition) {
            event.consume()
          }
        }
      }
    }
  )

  onKeyReleased = { event =>
    // Don't consume onKeyReleased as it might interfere with normal processing
    // and it doesn't add characters to the field anyway.
  }


//  def onDone(f: String => Unit): Unit = 
//    onDoneFunction = f

  val validProperty: BooleanProperty = new BooleanProperty()
  validProperty.value = false
  validProperty.onChange{(_,_,nv) =>
    disposition(nv)
  }

  def reset(): Unit = 
    text = ""

object NextField:
  def toChar(key: KeyCode): Char =
    key match
      case KeyCode.Space => ' '
      case KeyCode.Enter => '\n'
      case KeyCode.Tab   => '\t'
      case _ if key.isLetterKey => key.name.head
      case _ if key.isDigitKey  => key.name.last
      case _ => ' '
