package fdswarm.util

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import scala.util.Using

object Gzip:
  val DefaultMaxDecompressedBytes: Int = 64 * 1024 * 1024

  def compress(in: Array[Byte]): Array[Byte] =
    val bos = new ByteArrayOutputStream(in.length)

    Using.resource(new GZIPOutputStream(bos)) { gzip =>
      gzip.write(in)
    }

    bos.toByteArray

  def decompress(in: Array[Byte]): Array[Byte] =
    decompress(
      in,
      DefaultMaxDecompressedBytes
    )

  def decompress(
      in: Array[Byte],
      maxBytes: Int
  ): Array[Byte] =
    require(
      maxBytes >= 0,
      "maxBytes must be non-negative"
    )
    Using.resource(
      new GZIPInputStream(
        new ByteArrayInputStream(in)
      )
    ) { gis =>
      val out = new ByteArrayOutputStream(
        math.min(
          in.length,
          maxBytes
        )
      )
      val buffer = new Array[Byte](8192)
      var total = 0
      var read = gis.read(buffer)
      while read != -1 do
        if total + read > maxBytes then
          throw new IllegalArgumentException(
            s"GZIP payload exceeds maximum decompressed size of $maxBytes bytes"
          )
        out.write(buffer, 0, read)
        total += read
        read = gis.read(buffer)
      out.toByteArray
    }
