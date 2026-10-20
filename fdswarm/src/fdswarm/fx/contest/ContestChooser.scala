package fdswarm.fx.contest

import fdswarm.fx.utils.editor.CustomFieldEditor
import scalafx.beans.property.ObjectProperty
import scalafx.scene.Node
import scalafx.scene.control.{OverrunStyle, RadioButton, ToggleGroup}
import scalafx.scene.layout.{Region, VBox}

class ContestChooser(contestTypes: ContestTypes) extends CustomFieldEditor:
  private var contestTypeProperty: Option[ObjectProperty[ContestType]] =
    None

  override def isValid: Boolean =
    contestTypeProperty.exists(
      contestType =>
        contestType.value != null
    )

  override def editor(fieldProperty: Any): Node =
    fieldProperty match
      case p: ObjectProperty[?] =>
        val value = p.asInstanceOf[ObjectProperty[ContestType]]
        contestTypeProperty = Some(
          value
        )
        val tg = new ToggleGroup()

        val buttons: Seq[(ContestType, RadioButton)] =
          contestTypes.values.filter(_.allowChosing).toSeq.map { contestType =>
            val button = new RadioButton:
              text = contestType.displayName
              toggleGroup = tg
              minWidth = Region.USE_PREF_SIZE
              textOverrun = OverrunStyle.Clip
            contestType -> button
          }

        def syncSelection(
          contestType: ContestType
        ): Unit =
          buttons.find(_._1 == contestType) match
            case Some((_, button)) =>
              if !button.selected.value then
                button.selected = true
            case None =>
              tg.selectToggle(null)

        syncSelection(
          value.value
        )

        tg.selectedToggle.onChange { (_, _, newToggle) =>
          if newToggle != null then
            buttons.find(_._2.delegate == newToggle).foreach { case (contestType, _) =>
              if value.value != contestType then
                value.value = contestType
            }
        }

        value.onChange { (_, _, newValue) =>
          syncSelection(
            newValue
          )
        }

        val box = new VBox:
          spacing = 8

        box.children ++= buttons.map(_._2)
        box

      case _ =>
        throw new IllegalArgumentException(
          s"ContestChooser requires ObjectProperty[ContestType], got ${fieldProperty.getClass.getSimpleName}"
        )
