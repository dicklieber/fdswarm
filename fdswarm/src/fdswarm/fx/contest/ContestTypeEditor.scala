package fdswarm.fx.contest

import com.typesafe.config.ConfigFactory
import io.circe.Json
import scalafx.beans.property.{BooleanProperty, ObjectProperty}
import scalafx.scene.control.{CheckBox, Label, TextField}
import scalafx.scene.layout.{GridPane, Priority, Region, VBox}

import java.time.LocalTime
import scala.util.Try

/** Edits a private dialog draft; cancelling the dialog leaves the active contest alone. */
class ContestTypeEditor(contest: ObjectProperty[ContestType]) extends VBox(8):
  val valid: BooleanProperty = BooleanProperty(true)
  private var syncing = false
  private val grid = new GridPane:
    hgap = 12
    vgap = 8
    maxWidth = Double.MaxValue

  private def field(label: String, row: Int): TextField =
    val heading = new Label(label):
      wrapText = true
      minWidth = Region.USE_PREF_SIZE
    val input = new TextField:
      id = s"contest-type-field-$row"
      maxWidth = Double.MaxValue
    grid.add(heading, 0, row)
    grid.add(input, 1, row)
    GridPane.setHgrow(input, Priority.Always)
    input

  private val name = field("Contest name", 0)
  private val displayName = field("Display name", 1)
  private val cabrillo = field("Cabrillo contest name", 2)
  private val month = field("Month (1–12)", 3)
  private val weekend = field("Full weekend (1–5)", 4)
  private val start = field("Saturday start (UTC, HH:mm)", 5)
  private val end = field("Sunday final minute (UTC, HH:mm)", 6)
  private val setupEnabled = new CheckBox("Allow setup period")
  grid.add(setupEnabled, 0, 7, 2, 1)
  private val setupDays = field("Setup days relative to Saturday", 8)
  private val setupStart = field("Earliest setup start (UTC, HH:mm)", 9)
  private val setupHours = field("Maximum setup hours", 10)
  private val error = new Label:
    wrapText = true
    style = "-fx-text-fill: #a00000;"
    maxWidth = Double.MaxValue
  children ++= Seq(grid, error)

  private def load(value: ContestType): Unit =
    syncing = true
    try
      val rule = value.compute.rule
      name.text = value.name
      displayName.text = value.displayName
      cabrillo.text = value.cabrilloContestName
      month.text = rule.getInt("month").toString
      weekend.text = rule.getInt("fullWeekend").toString
      start.text = rule.getString("startUtc")
      end.text = rule.getString("endUtc")
      setupEnabled.selected = rule.hasPath("setup")
      setupDays.text = if rule.hasPath("setup") then rule.getInt("setup.daysFromSaturday").toString else "-1"
      setupStart.text = if rule.hasPath("setup") then rule.getString("setup.startUtc") else "16:00"
      setupHours.text = if rule.hasPath("setup") then
        (rule.getDuration("setup.maxCumulativeDuration").toMinutes / 60.0).toString
      else "12"
      valid.value = true
      error.text = ""
    finally syncing = false
    updateSetupFields()

  private def updateSetupFields(): Unit =
    Seq(setupDays, setupStart, setupHours).foreach(_.disable = !setupEnabled.selected.value)

  private def publish(): Unit =
    updateSetupFields()
    if !syncing then
      val result = Try {
        require(ContestTypes.configured.get(name.text.value.trim).nonEmpty,
          "Contest name must be ARRL or WFD; class choices follow this name.")
        val startTime = LocalTime.parse(start.text.value.trim)
        val endTime = LocalTime.parse(end.text.value.trim)
        var rule = Json.obj(
          "month" -> Json.fromInt(month.text.value.trim.toInt),
          "fullWeekend" -> Json.fromInt(weekend.text.value.trim.toInt),
          "startUtc" -> Json.fromString(startTime.toString),
          "endUtc" -> Json.fromString(endTime.toString)
        )
        if setupEnabled.selected.value then
          val minutes = (BigDecimal(setupHours.text.value.trim) * 60).toLongExact
          require(minutes > 0, "Maximum setup hours must be positive")
          rule = rule.mapObject(_.add("setup", Json.obj(
            "daysFromSaturday" -> Json.fromInt(setupDays.text.value.trim.toInt),
            "startUtc" -> Json.fromString(LocalTime.parse(setupStart.text.value.trim).toString),
            "maxCumulativeDuration" -> Json.fromString(s"$minutes minutes")
          )))
        ContestType(
          name.text.value.trim,
          displayName.text.value.trim,
          ContestDateRule(ConfigFactory.parseString(rule.noSpaces)),
          cabrillo.text.value.trim
        )
      }
      valid.value = result.isSuccess
      error.text = result.failed.toOption.map(e => Option(e.getMessage).getOrElse("Invalid contest definition")).getOrElse("")
      result.foreach { value =>
        syncing = true
        try contest.value = value
        finally syncing = false
      }

  Seq(name, displayName, cabrillo, month, weekend, start, end, setupDays, setupStart, setupHours)
    .foreach(_.text.onChange { (_, _, _) => publish() })
  setupEnabled.selected.onChange { (_, _, _) => publish() }
  contest.onChange { (_, _, value) => if !syncing then load(value) }
  load(contest.value)
