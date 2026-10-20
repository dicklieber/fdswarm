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

package fdswarm.fx.qso

import fdswarm.fx.contest.{ContestCatalog, ContestConfigManager, ContestDefinition}
import fdswarm.fx.{NextField, UserConfig}
import jakarta.inject.*
import scalafx.scene.control.{TextField, TextFormatter}

class ContestClassField @Inject() (
                                    contestManager: ContestConfigManager,
                                    contestCatalog: ContestCatalog,
                                    dupPanel: DupPanel,
                                    override val userConfig: UserConfig
) extends TextField
    with NextField:
  private def classChars: String =
    contestCatalog
      .getChars(
        contestManager.contestType
      )
      .toUpperCase

  private def typingPattern: String =
    val chars = classChars
    if chars.nonEmpty then
      "^([0-9]{1,2}[" + chars + "]|[0-9]{0,2})$"
    else
      "^[0-9]{0,2}$"

  private def classPattern: Option[String] =
    val chars = classChars
    if chars.nonEmpty then
      Some("^[0-9]{1,2}[" + chars + "]$")
    else
      None

  private def showHelp(): Unit =
    val contest: Option[ContestDefinition] = contestCatalog.getContest(contestManager.contestType)
    contest.foreach { contest =>
      val items = contest.classChoices.map(contestClassChar => (contestClassChar.ch, contestClassChar.description))
      dupPanel.show(s"${contestManager.contestType.name} Classes", items)
    }

  focused.onChange { (_, _, newValue) =>
    val currentText = text.value
    if newValue && !currentText.matches(typingPattern) then showHelp()
  }

  textFormatter = new TextFormatter[String]((change: TextFormatter.Change) => {
    if (change.isContentChange) {
      change.setText(change.getText.toUpperCase)
    }
    val newText = change.controlNewText
    // Match partial strings during typing. If no contest classes are available,
    // allow only 0-2 digits to avoid invalid character class regexes.
    if (newText.matches(typingPattern))
      change
    else
      // Only show help if the rejected character makes it clearly invalid,
      // and not just a prefix that could be valid.
      // For ContestClassField, typingPattern already allows 0-2 digits.
      // If it doesn't match, it means it's definitely wrong for the pattern.
      showHelp()
      null
  })

  text.onChange { (_, _, nv) =>
    validProperty.value = isValid(nv)
  }

  override def isTransitionKey(key: scalafx.scene.input.KeyCode): Boolean =
    super.isTransitionKey(key) || key.isLetterKey

  override def isValid(str: String): Boolean =
    classPattern.exists(str.matches)
