package fdswarm.fx.contest

import com.dlsc.gemsfx.TimePicker
import fdswarm.fx.utils.editor.CustomFieldEditor
import scalafx.beans.property.{BooleanProperty, ObjectProperty}
import scalafx.scene.Node
import scalafx.scene.control.{CheckBox, DatePicker, Label}
import scalafx.scene.layout.{GridPane, Priority, Region, StackPane, VBox}
import java.time.{LocalDateTime, ZoneId}

/** A local date/time used only for the optional contest-end display. */
class OptionalEndTimeEditor extends CustomFieldEditor:
  val valid: BooleanProperty = BooleanProperty(true)
  override def isValid: Boolean = valid.value

  override def editor(fieldProperty: Any): Node =
    val property = fieldProperty.asInstanceOf[ObjectProperty[Option[LocalDateTime]]]
    val initial = property.value.getOrElse(LocalDateTime.now().withSecond(0).withNano(0))
    val enabled = new CheckBox("Show contest end countdown"):
      minWidth = Region.USE_PREF_SIZE
      selected = property.value.isDefined
    val date = new DatePicker(initial.toLocalDate):
      editable = false
      minWidth = Region.USE_PREF_SIZE
      maxWidth = Double.MaxValue
    val time = new TimePicker
    time.setClockType(TimePicker.ClockType.TWENTY_FOUR_HOUR_CLOCK)
    time.setFormat(TimePicker.Format.HOURS_MINUTES)
    time.setStepRateInMinutes(1)
    time.setTime(initial.toLocalTime)
    time.setMinWidth(Region.USE_PREF_SIZE)
    val timeNode = new StackPane:
      minWidth = Region.USE_PREF_SIZE
      delegate.getChildren.add(time)
    val fields = new GridPane:
      hgap = 8
      vgap = 8
    Seq("Date" -> date, "Time (24-hour)" -> timeNode)
      .zipWithIndex.foreach { case ((text, input), row) =>
        val heading = new Label(text):
          minWidth = Region.USE_PREF_SIZE
        fields.add(heading, 0, row)
        fields.add(input, 1, row)
      }
    GridPane.setHgrow(date, Priority.Always)
    val zone = new Label(s"Local time (${ZoneId.systemDefault().getId})"):
      wrapText = true
      minHeight = Region.USE_PREF_SIZE
      maxWidth = Double.MaxValue

    def publish(): Unit =
      fields.disable = !enabled.selected.value
      valid.value = !enabled.selected.value || (date.value.value != null && time.getTime != null)
      if !enabled.selected.value then property.value = None
      else if valid.value then
        property.value = Some(LocalDateTime.of(date.value.value, time.getTime))

    enabled.selected.onChange { (_, _, _) => publish() }
    date.value.onChange { (_, _, _) => publish() }
    time.timeProperty().addListener((_: javafx.beans.Observable) => publish())
    fields.disable = !enabled.selected.value
    new VBox(8):
      fillWidth = true
      maxWidth = Double.MaxValue
      children = Seq(enabled, zone, fields)
