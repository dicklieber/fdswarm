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

package fdswarm.fx.utils.editor

import fdswarm.fx.utils.GridCells
import fdswarm.util.camelToWords
import javafx.scene.input.{KeyCode as JfxKeyCode, KeyEvent as JfxKeyEvent}
import scalafx.beans.property.*
import scalafx.scene.Node
import scalafx.scene.control.*
import scalafx.scene.layout.*
import scalafx.geometry.Pos

import java.lang.reflect.Constructor
import scala.collection.mutable
import scala.jdk.CollectionConverters.*

/**
 * Editor for an arbitrary case class.
 *
 * @param target what we start with.
 * @tparam T type of the case class.
 */
class CaseClassPropertyEditor[T <: Product](val target: T):
  private val runtimeClass: Class[?] =
    target.getClass

  private val fieldNames: Vector[String] =
    target.productElementNames.toVector

  private val propertiesInOrder: Vector[(String, FieldValue)] =
    buildProperties(target)

  private val propertiesByName: Map[String, FieldValue] =
    propertiesInOrder.toMap

  private val propertyT: ObjectProperty[T] =
    ObjectProperty[T](target)
  private val anyFieldChangeListeners =
    mutable.ArrayBuffer.empty[() => Unit]

  def currentValueProperty: ObjectProperty[T] =
    propertyT

  private val customEditors =
    mutable.LinkedHashMap.empty[String, CustomFieldEditor]
  private val hiddenFields =
    mutable.HashSet.empty[String]

  private def updatePropertyT(): Unit =
    if hasNoEmptyStringFields then
      propertyT.value = rebuildTarget().asInstanceOf[T]

  propertiesInOrder.foreach { case (_, fieldValue) =>
    fieldValue.onAnyChange(
      () =>
        updatePropertyT()
        anyFieldChangeListeners.foreach(
          listener =>
            listener()
        )
    )
  }

  def onAnyFieldChange(
    listener: () => Unit
  ): Unit =
    anyFieldChangeListeners += listener

  def getProperty[A](propertyName: String): A =
    propertiesByName.getOrElse(
      propertyName,
      throw new NoSuchElementException(s"No field named '$propertyName'")
    ).rawProperty.asInstanceOf[A]

  def setCustomEditor(
    fieldName: String,
    editor: CustomFieldEditor
  ): Unit =
    require(fieldNames.contains(fieldName), s"No field named '$fieldName'")
    customEditors(fieldName) = editor

  def hideField(
    fieldName: String
  ): Unit =
    require(
      fieldNames.contains(
        fieldName
      ),
      s"No field named '$fieldName'"
    )
    hiddenFields += fieldName

  def horizontal: Pane =
    val grid = GridCells.styledGrid("case-class-editor-grid")
    val fieldEditorsInOrder = mutable.ArrayBuffer.empty[Node]
    val visibleProperties = propertiesInOrder.filterNot {
      case (
            fieldName,
            _
          ) =>
        hiddenFields.contains(
          fieldName
        )
    }

    for ((fieldName, fieldValue), col) <- visibleProperties.zipWithIndex do
      val helpInfo = helpInfoFor(fieldName)
      val label = new Label(camelToWords(fieldName)):
        minWidth = Region.USE_PREF_SIZE
        textOverrun = OverrunStyle.Clip
        styleClass.add("grid-row-label")
        styleClass.add("grid-header-primary-text")
        styleClass.add("grid-cell")
        helpInfo.foreach(fh => tooltip = new Tooltip(fh.value()))
      val labelNode = labelWithHelpLink(label, helpInfo, fieldName)

      val editorNode = nodeFor(fieldName, fieldValue)
      editorNode.styleClass.add("grid-value")
      if fieldValue.isNumeric then editorNode.styleClass.add("gridNumber")
      editorNode.styleClass.add("grid-cell")
      fieldEditorsInOrder += editorNode

      val labelCell = GridCells.addCell(grid, col, 0, labelNode, "grid-cell", "grid-row-label", "grid-header-primary-cell")
      val valueCell = GridCells.addCell(grid, col, 1, editorNode, "grid-cell", "grid-value")

      if fieldValue.isNumeric then valueCell.styleClass.add("gridNumber")

      GridPane.setHgrow(labelCell, Priority.Never)
      GridPane.setHgrow(valueCell, Priority.Always)

    installKeyboardFieldTraversal(
      root = grid,
      fieldEditors = fieldEditorsInOrder.toVector
    )
    grid

  def vertical: Pane =
    val grid = GridCells.styledGrid("case-class-editor-grid")
    val fieldEditorsInOrder = mutable.ArrayBuffer.empty[Node]
    val visibleProperties = propertiesInOrder.filterNot {
      case (
            fieldName,
            _
          ) =>
        hiddenFields.contains(
          fieldName
        )
    }

    for ((fieldName, fieldValue), row) <- visibleProperties.zipWithIndex do
      val helpInfo = helpInfoFor(fieldName)
      val label = new Label(camelToWords(fieldName)):
        minWidth = Region.USE_PREF_SIZE
        textOverrun = OverrunStyle.Clip
        styleClass.add("grid-row-label")
        styleClass.add("grid-cell")
        helpInfo.foreach(fh => tooltip = new Tooltip(fh.value()))
      val labelNode = labelWithHelpLink(label, helpInfo, fieldName)

      val editorNode = nodeFor(fieldName, fieldValue)
      editorNode.styleClass.add("grid-value")
      if fieldValue.isNumeric then editorNode.styleClass.add("gridNumber")
      editorNode.styleClass.add("grid-cell")
      fieldEditorsInOrder += editorNode

      val labelCell = GridCells.addCell(grid, 0, row, labelNode, "grid-cell", "grid-row-label")
      val valueCell = GridCells.addCell(grid, 1, row, editorNode, "grid-cell", "grid-value")

      if fieldValue.isNumeric then valueCell.styleClass.add("gridNumber")

      GridPane.setHgrow(labelCell, Priority.Never)
      GridPane.setHgrow(valueCell, Priority.Always)

    installKeyboardFieldTraversal(
      root = grid,
      fieldEditors = fieldEditorsInOrder.toVector
    )
    grid

  /**
   * Finish editing and return the target.
   */
  def finish(): T =
    propertyT.value

  def isValid: Boolean =
    propertiesInOrder
      .iterator
      .map(_._1)
      .filterNot(hiddenFields.contains)
      .flatMap(customEditors.get)
      .forall(_.isValid)

  private def nodeFor(fieldName: String, fieldValue: FieldValue): Node =
    customEditors.get(fieldName) match
      case Some(custom) =>
        val node = custom.editor(
          fieldValue.rawProperty
        )
        node.delegate.getProperties.put(
          CaseClassPropertyEditor.CustomFieldEditorNodeKey,
          custom
        )
        node
      case None         => defaultEditor(fieldValue)

  private def buildProperties(value: T): Vector[(String, FieldValue)] =
    val values = value.productIterator.toVector
    fieldNames.zip(values).map { (name, fieldValue) =>
      name -> newFieldValue(fieldValue)
    }

  private def newFieldValue(value: Any): FieldValue =
    value match
      case v: String  => StringFieldValue(StringProperty(v))
      case v: Int     => IntFieldValue(IntegerProperty(v))
      case v: Long    => LongFieldValue(LongProperty(v))
      case v: Double  => DoubleFieldValue(DoubleProperty(v))
      case v: Boolean => BooleanFieldValue(BooleanProperty(v))
      case v          => ObjectFieldValue(ObjectProperty[Any](v))

  private def rebuildTarget(): Any =
    val ctor = primaryConstructor(runtimeClass)
    val args = propertiesInOrder.map { (_, fieldValue) =>
      fieldValue.valueAsObject
    }
    ctor.newInstance(args*)

  private def hasNoEmptyStringFields: Boolean =
    propertiesInOrder.forall {
      case (_, StringFieldValue(p)) =>
        Option(p.value).exists(_.nonEmpty)
      case _ =>
        true
    }

  private def defaultEditor(fieldValue: FieldValue): Node =
    fieldValue match
      case StringFieldValue(p) =>
        new TextField:
          text <==> p

      case IntFieldValue(p) =>
        new TextField:
          text = p.value.toString
          text.onChange { (_, _, nv) =>
            parseIntValue(nv).foreach(v => p.value = v)
          }
          p.onChange { (_, _, _) =>
            val next = p.value.toString
            if text.value != next then text.value = next
          }

      case LongFieldValue(p) =>
        new TextField:
          text = p.value.toString
          text.onChange { (_, _, nv) =>
            parseLongValue(nv).foreach(v => p.value = v)
          }
          p.onChange { (_, _, _) =>
            val next = p.value.toString
            if text.value != next then text.value = next
          }

      case DoubleFieldValue(p) =>
        new TextField:
          text = p.value.toString
          text.onChange { (_, _, nv) =>
            parseDoubleValue(nv).foreach(v => p.value = v)
          }
          p.onChange { (_, _, _) =>
            val next = p.value.toString
            if text.value != next then text.value = next
          }

      case BooleanFieldValue(p) =>
        new CheckBox:
          selected <==> p

      case ObjectFieldValue(p) =>
        new TextField:
          text = Option(p.value).map(_.toString).getOrElse("")
          disable = true
          promptText = "Unsupported type for direct editing"
          p.onChange { (_, _, _) =>
            text.value = Option(p.value).map(_.toString).getOrElse("")
          }

  /** Returns the [[FieldHelp]] annotation for `fieldName`, or `None`. */
  private def helpInfoFor(fieldName: String): Option[FieldHelp] =
    // Scala 3 emits @Target(FIELD) annotations on the backing field.
    // As a fallback we also check the primary constructor parameter.
    val fromField: Option[FieldHelp] =
      try
        val f = runtimeClass.getDeclaredField(fieldName)
        Option(f.getAnnotation(classOf[FieldHelp]))
      catch
        case _: NoSuchFieldException => None
    fromField.orElse {
      val paramIndex = fieldNames.indexOf(fieldName)
      if paramIndex < 0 then None
      else
        val ctor = runtimeClass.getDeclaredConstructors.maxBy(_.getParameterCount)
        val params = ctor.getParameters
        if paramIndex < params.length then
          Option(params(paramIndex).getAnnotation(classOf[FieldHelp]))
        else None
    }

  /**
   * Wraps `label` in an [[HBox]] containing a small `"?"` [[Hyperlink]] when
   * `@FieldHelp` is present.
   *
   * The URL passed to [[HelpBrowser.open]] is taken from the annotation's
   * `url` element when non-empty; otherwise `fieldName` is used as a bare
   * relative path (resolved to `baseUrl/fdswarmDocs/help/<fieldName>`).
   *
   * Returns `label` unchanged when no `@FieldHelp` annotation is present.
   */
  private def labelWithHelpLink(label: Label, helpInfo: Option[FieldHelp], fieldName: String): Node =
    helpInfo match
      case None => label
      case Some(fh) =>
        val url = fh.url() match
          case "" => fieldName
          case u  => u
        val link = new Hyperlink("?"):
          styleClass.add("field-help-link")
          onAction = _ => HelpBrowser.open(url)
        new HBox:
          spacing = 2
          alignment = Pos.CenterLeft
          children = Seq(label, link)

  private def primaryConstructor(clazz: Class[?]): Constructor[?] =
    val ctor = clazz.getDeclaredConstructors.maxBy(_.getParameterCount)
    ctor.setAccessible(true)
    ctor

  private def parseIntValue(s: String): Option[Int] =
    try Some(s.trim.toInt)
    catch
      case _: NumberFormatException => None

  private def parseLongValue(s: String): Option[Long] =
    try Some(s.trim.toLong)
    catch
      case _: NumberFormatException => None

  private def parseDoubleValue(s: String): Option[Double] =
    try Some(s.trim.toDouble)
    catch
      case _: NumberFormatException => None

  private def installKeyboardFieldTraversal(
    root: Pane,
    fieldEditors: Seq[Node]
  ): Unit =
    val focusTargets = fieldEditors.flatMap(
      editor =>
        primaryFocusable(
          editor.delegate
        )
    ).toVector

    focusTargets.foreach(
      _.setFocusTraversable(true)
    )

    root.delegate.addEventFilter(
      JfxKeyEvent.KEY_PRESSED,
      (event: JfxKeyEvent) =>
        if event.getCode == JfxKeyCode.TAB then
          event.getTarget match
            case sourceNode: javafx.scene.Node =>
              val currentIndex = focusTargets.indexWhere(
                target =>
                  isSameOrDescendant(
                    sourceNode,
                    target
                  )
              )

              if currentIndex >= 0 then
                val direction = if event.isShiftDown then -1 else 1
                findNextFocusableIndex(
                  focusTargets = focusTargets,
                  currentIndex = currentIndex,
                  direction = direction
                ).foreach { nextIndex =>
                  focusTargets(
                    nextIndex
                  ).requestFocus()
                  event.consume()
                }
            case _ =>
              ()
    )

  private def primaryFocusable(
    node: javafx.scene.Node
  ): Option[javafx.scene.Node] =
    node match
      case control: javafx.scene.control.Control =>
        control.setFocusTraversable(true)
        Some(control)
      case parent: javafx.scene.Parent =>
        parent.getChildrenUnmodifiable.asScala.view.flatMap(
          primaryFocusable
        )
          .headOption
      case _ =>
        None

  private def findNextFocusableIndex(
    focusTargets: Vector[javafx.scene.Node],
    currentIndex: Int,
    direction: Int
  ): Option[Int] =
    var nextIndex = currentIndex + direction
    while nextIndex >= 0 && nextIndex < focusTargets.length do
      if canFocus(
        focusTargets(nextIndex)
      ) then
        return Some(nextIndex)
      nextIndex = nextIndex + direction
    None

  private def canFocus(
    node: javafx.scene.Node
  ): Boolean =
    node.isVisible && !node.isDisable && node.isFocusTraversable

  private def isSameOrDescendant(
    node: javafx.scene.Node,
    ancestor: javafx.scene.Node
  ): Boolean =
    var current: javafx.scene.Node = node
    while current != null do
      if current eq ancestor then
        return true
      current = current.getParent
    false


sealed trait FieldValue:
  def rawProperty: Any
  def isNumeric: Boolean
  def setFromAny(value: Any): Unit
  def valueAsObject: Object
  def onAnyChange(listener: () => Unit): Unit

final case class StringFieldValue(property: StringProperty) extends FieldValue:
  override def rawProperty: Any = property
  override def isNumeric: Boolean = false
  override def setFromAny(value: Any): Unit =
    property.value = value.asInstanceOf[String]
  override def valueAsObject: Object =
    property.value
  override def onAnyChange(listener: () => Unit): Unit =
    property.onChange { (_, _, _) => listener() }

final case class IntFieldValue(property: IntegerProperty) extends FieldValue:
  override def rawProperty: Any = property
  override def isNumeric: Boolean = true
  override def setFromAny(value: Any): Unit =
    property.value = value.asInstanceOf[Int]
  override def valueAsObject: Object =
    Int.box(property.value)
  override def onAnyChange(listener: () => Unit): Unit =
    property.onChange { (_, _, _) => listener() }

final case class LongFieldValue(property: LongProperty) extends FieldValue:
  override def rawProperty: Any = property
  override def isNumeric: Boolean = true
  override def setFromAny(value: Any): Unit =
    property.value = value.asInstanceOf[Long]
  override def valueAsObject: Object =
    Long.box(property.value)
  override def onAnyChange(listener: () => Unit): Unit =
    property.onChange { (_, _, _) => listener() }

final case class DoubleFieldValue(property: DoubleProperty) extends FieldValue:
  override def rawProperty: Any = property
  override def isNumeric: Boolean = true
  override def setFromAny(value: Any): Unit =
    property.value = value.asInstanceOf[Double]
  override def valueAsObject: Object =
    Double.box(property.value)
  override def onAnyChange(listener: () => Unit): Unit =
    property.onChange { (_, _, _) => listener() }

final case class BooleanFieldValue(property: BooleanProperty) extends FieldValue:
  override def rawProperty: Any = property
  override def isNumeric: Boolean = false
  override def setFromAny(value: Any): Unit =
    property.value = value.asInstanceOf[Boolean]
  override def valueAsObject: Object =
    Boolean.box(property.value)
  override def onAnyChange(listener: () => Unit): Unit =
    property.onChange { (_, _, _) => listener() }

final case class ObjectFieldValue(property: ObjectProperty[Any]) extends FieldValue:
  override def rawProperty: Any = property
  override def isNumeric: Boolean = false
  override def setFromAny(value: Any): Unit =
    property.value = value
  override def valueAsObject: Object =
    property.value.asInstanceOf[Object]
  override def onAnyChange(listener: () => Unit): Unit =
    property.onChange { (_, _, _) => listener() }

trait CustomFieldEditor:
  def editor(fieldProperty: Any): Node
  def isValid: Boolean

object CaseClassPropertyEditor:
  val CustomFieldEditorNodeKey: String =
    "fdswarm.customFieldEditor"
