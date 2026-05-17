package fdswarm.util

import munit.FunSuite

import java.nio.charset.StandardCharsets

class GzipTest extends FunSuite:

  test("compress and decompress should round-trip UTF-8 text"):
    val input = "Field Day logging data: N0CALL 20m CW".getBytes(StandardCharsets.UTF_8)

    val compressed = Gzip.compress(
      input
    )
    val restored = Gzip.decompress(
      compressed
    )

    assertEquals(
      restored.toSeq,
      input.toSeq
    )

  test("compress and decompress should round-trip empty input"):
    val input = Array.emptyByteArray

    val compressed = Gzip.compress(
      input
    )
    val restored = Gzip.decompress(
      compressed
    )

    assertEquals(
      restored.toSeq,
      input.toSeq
    )

  test("decompress should fail for non-gzip input"):
    val invalid = "not gzipped".getBytes(StandardCharsets.UTF_8)

    intercept[java.io.IOException](
      Gzip.decompress(
        invalid
      )
    )

  test("decompress should reject output larger than maxBytes"):
    val input = "Field Day logging data: N0CALL 20m CW".getBytes(StandardCharsets.UTF_8)
    val compressed = Gzip.compress(
      input
    )

    intercept[IllegalArgumentException](
      Gzip.decompress(
        compressed,
        input.length - 1
      )
    )
