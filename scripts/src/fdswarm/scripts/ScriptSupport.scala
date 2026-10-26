package fdswarm.scripts

import io.circe.Json
import java.io.{PrintWriter, StringWriter}
import java.nio.file.{Files, Path, StandardCopyOption}
import java.util.concurrent.TimeUnit
import org.w3c.dom.{Document, Element}
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.{OutputKeys, TransformerFactory}
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import scala.jdk.CollectionConverters.*
import scala.util.Using

object ScriptSupport:
  def repositoryRoot: Path =
    val cwd = Path.of("").toAbsolutePath.normalize()
    Iterator.iterate(cwd)(_.getParent).takeWhile(_ != null)
      .find(path => Files.isRegularFile(path.resolve("build.mill")))
      .getOrElse(throw new IllegalArgumentException("Run this command from the fdswarm repository"))

  def write(path: Path, text: String): Unit =
    Files.writeString(path, text)
    ()

  def jsonField(json: Json, name: String): Json =
    json.hcursor.downField(name).focus.getOrElse(throw new java.io.IOException(s"Missing JSON field: $name"))

  def jsonString(json: Json, name: String): String =
    jsonField(json, name).asString.getOrElse(throw new java.io.IOException(s"Expected string field: $name"))

  def jsonArray(json: Json, name: String): Vector[Json] =
    jsonField(json, name).asArray.getOrElse(throw new java.io.IOException(s"Expected array field: $name"))

  def trace(error: Throwable): String =
    val output = new StringWriter()
    error.printStackTrace(new PrintWriter(output))
    output.toString

  def xmlFactory: DocumentBuilderFactory =
    val factory = DocumentBuilderFactory.newInstance()
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    factory.setExpandEntityReferences(false)
    factory

  def readXml(path: Path): Document = xmlFactory.newDocumentBuilder().parse(path.toFile)

  def children(parent: Element, tag: String): Vector[Element] =
    val nodes = parent.getChildNodes
    (0 until nodes.getLength).flatMap { index =>
      nodes.item(index) match
        case element: Element if element.getTagName == tag => Some(element)
        case _ => None
    }.toVector

  def descendants(parent: Element, tag: String): Vector[Element] =
    val nodes = parent.getElementsByTagName(tag)
    (0 until nodes.getLength).map(index => nodes.item(index).asInstanceOf[Element]).toVector

  def attribute(element: Element, name: String, default: String = ""): String =
    if element.hasAttribute(name) then element.getAttribute(name) else default

  def append(document: Document, parent: Element, tag: String, attributes: (String, String)*): Element =
    val element = document.createElement(tag)
    attributes.foreach((name, value) => element.setAttribute(name, value))
    parent.appendChild(element)
    element

  def writeXml(document: Document, path: Path): Unit =
    val transformer = TransformerFactory.newInstance().newTransformer()
    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
    transformer.setOutputProperty(OutputKeys.INDENT, "yes")
    transformer.transform(new DOMSource(document), new StreamResult(path.toFile))

  def copyTree(source: Path, destination: Path): Unit =
    Using.resource(Files.walk(source)) { paths =>
      paths.iterator().asScala.foreach { path =>
        val target = destination.resolve(source.relativize(path))
        if Files.isDirectory(path) then Files.createDirectories(target)
        else Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
      }
    }

  def deleteTree(path: Path): Unit =
    if Files.exists(path) then
      Using.resource(Files.walk(path)) { paths =>
        paths.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.delete)
      }

  def stop(process: Process): Unit =
    if process.isAlive then
      val descendants = process.descendants().toList.asScala.toVector
      descendants.foreach(_.destroy())
      process.destroy()
      if !process.waitFor(10, TimeUnit.SECONDS) then
        descendants.foreach(_.destroyForcibly())
        process.destroyForcibly()
        process.waitFor()
