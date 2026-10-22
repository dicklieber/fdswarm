package fdswarm.util

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.Base64
import com.typesafe.config.{Config, ConfigList, ConfigObject, ConfigValue}
import scala.jdk.CollectionConverters.*

object FingerPrint:
  /** First 8 bytes of SHA-256, encoded as 11 unpadded URL-safe Base64 characters.
    * Hashes the named fields in declaration order, excluding `omit`.
    * Descends into products, sequences and Typesafe Config values. Config object
    * keys are sorted; sequence order is preserved. Other values use their string
    * representations, which must be deterministic for a stable fingerprint.
    * Names and values are length-prefixed UTF-8 strings, with structural markers
    * preserving nested boundaries. Omission applies only to the supplied product.
    */
  def fingerprint8(p: Product, omit: String): String =
    val digest = MessageDigest.getInstance("SHA-256")

    def marker(value: Int): Unit =
      digest.update(ByteBuffer.allocate(4).putInt(value).array())

    def update(value: Any): Unit = value match
      case null => marker(-1)
      case config: Config => update(config.root())
      case obj: ConfigObject =>
        marker(-2)
        obj.asScala.toSeq.sortBy(_._1).foreach { (name, child) =>
          update(name)
          update(child)
        }
        marker(-3)
      case list: ConfigList =>
        marker(-4)
        list.asScala.foreach(update)
        marker(-3)
      case config: ConfigValue => update(config.unwrapped())
      case seq: Seq[?] =>
        marker(-4)
        seq.foreach(update)
        marker(-3)
      case product: Product =>
        marker(-5)
        product.productElementNames.zip(product.productIterator).foreach { (name, child) =>
          update(name)
          update(child)
        }
        marker(-3)
      case _ =>
        val bytes = value.toString.getBytes(UTF_8)
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array())
        digest.update(bytes)

    p.productElementNames.zip(p.productIterator).foreach { (name, value) =>
      if name != omit then
        update(name)
        update(value)
    }

    Base64.getUrlEncoder.withoutPadding().encodeToString(digest.digest().take(8))
