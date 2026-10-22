package fdswarm.util

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.Base64

object FingerPrint:
  /** First 8 bytes of SHA-256, encoded as 11 unpadded URL-safe Base64 characters.
    * Hashes the named fields in declaration order, excluding `omit`.
    * Values use their string representations, which must be deterministic for a
    * stable fingerprint. Names and values are length-prefixed UTF-8 strings to
    * preserve field boundaries. Omission applies only to the supplied product.
    */
  def fingerprint8(p: Product, omit: String): String =
    val digest = MessageDigest.getInstance("SHA-256")

    def update(value: Any): Unit =
      if value == null then
        digest.update(ByteBuffer.allocate(4).putInt(-1).array())
      else
        val bytes = value.toString.getBytes(UTF_8)
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array())
        digest.update(bytes)

    p.productElementNames.zip(p.productIterator).foreach { (name, value) =>
      if name != omit then
        update(name)
        update(value)
    }

    Base64.getUrlEncoder.withoutPadding().encodeToString(digest.digest().take(8))
