package fdswarm.fx.contest

import fdswarm.JavaFxTestKit
import munit.FunSuite
import scalafx.beans.property.ObjectProperty

class ContestTypeEditorTest extends FunSuite:
  test("valid edits update only the dialog draft"):
    JavaFxTestKit.runOnFx {
      val original = ContestTypes.configured("WFD")
      val draft = ObjectProperty(original)
      val editor = new ContestTypeEditor(draft)
      val display = editor.delegate.lookup("#contest-type-field-1").asInstanceOf[javafx.scene.control.TextField]
      display.setText("Local Winter Field Day")
      assert(editor.valid.value)
      assertEquals(draft.value.displayName, "Local Winter Field Day")
      assertEquals(ContestTypes.configured("WFD"), original)
    }

  test("invalid date edits block saving without publishing an invalid draft"):
    JavaFxTestKit.runOnFx {
      val original = ContestTypes.configured("WFD")
      val draft = ObjectProperty(original)
      val editor = new ContestTypeEditor(draft)
      val month = editor.delegate.lookup("#contest-type-field-3").asInstanceOf[javafx.scene.control.TextField]
      month.setText("13")
      assert(!editor.valid.value)
      assertEquals(draft.value, original)
      month.setText("2")
      assert(editor.valid.value)
      assertEquals(draft.value.compute.rule.getInt("month"), 2)
    }

  test("chooser synchronization retains an edited definition"):
    JavaFxTestKit.runOnFx {
      val draft = ObjectProperty(ContestTypes.configured("WFD"))
      val chooser = new ContestChooser(ContestTypes.configured)
      chooser.editor(draft)
      val edited = draft.value.copy(displayName = "Edited")
      draft.value = edited
      assertEquals(draft.value, edited)
    }
