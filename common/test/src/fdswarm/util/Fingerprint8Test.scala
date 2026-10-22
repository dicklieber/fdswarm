package fdswarm.util

import fdswarm.fx.contest.ContestTypes
import fdswarm.util.FingerPrint.fingerprint8
import munit.FunSuite

import java.util.Base64

class Fingerprint8Test extends FunSuite:
  private case class Entry(name: String, count: Int, ignored: String)
  private case class Pair(first: String, second: String)
  private case class Nested(entry: Entry)
  private case class Named(name: String, detail: String):
    override def toString: String = name
  private case class Wrapped(value: Named)
  private case class Items(values: Seq[String])

  test("fingerprints retain 8 SHA-256 bytes as unpadded URL-safe Base64"):
    val entry = Entry("W1AW", 3, "local")
    val result = fingerprint8(entry, "ignored")
    assertEquals(result, "G53T9P2-bD8")
    assertEquals(result, fingerprint8(entry.copy(), "ignored"))
    assertEquals(Base64.getUrlDecoder.decode(result).length, 8)
    assertEquals(result.length, 11)
    assert(result.matches("[A-Za-z0-9_-]{11}"))

  test("only the named field is omitted"):
    val entry = Entry("W1AW", 3, "local")
    assertEquals(fingerprint8(entry, "ignored"), fingerprint8(entry.copy(ignored = "remote"), "ignored"))
    assertNotEquals(fingerprint8(entry, "ignored"), fingerprint8(entry.copy(count = 4), "ignored"))
    assertNotEquals(fingerprint8(entry, "missing"), fingerprint8(entry.copy(ignored = "remote"), "missing"))

  test("field boundaries and order are preserved"):
    assertNotEquals(fingerprint8(Pair("ab", "c"), ""), fingerprint8(Pair("a", "bc"), ""))
    assertNotEquals(fingerprint8(Pair("a", "b"), ""), fingerprint8(Pair("b", "a"), ""))

  test("null differs from a literal null string"):
    assertNotEquals(fingerprint8(Pair(null, ""), ""), fingerprint8(Pair("null", ""), ""))

  test("an empty product hashes the empty input"):
    assertEquals(fingerprint8(EmptyTuple, ""), "47DEQpj8HBQ")

  test("ContestType is supported without special handling"):
    val contest = ContestTypes.configured("WFD")
    assertEquals(fingerprint8(contest, "displayName"),
      fingerprint8(contest.copy(displayName = "Local name"), "displayName"))

  test("nested products use fields rather than their overridden toString"):
    val original = Wrapped(Named("same", "first"))
    val changed = Wrapped(Named("same", "second"))
    assertNotEquals(fingerprint8(original, ""), fingerprint8(changed, ""))

  test("omission does not apply to nested fields"):
    val original = Nested(Entry("W1AW", 3, "local"))
    val changed = Nested(original.entry.copy(ignored = "remote"))
    assertNotEquals(fingerprint8(original, "ignored"), fingerprint8(changed, "ignored"))

  test("sequences preserve element boundaries and order"):
    assertNotEquals(fingerprint8(Items(Seq("a", "b")), ""), fingerprint8(Items(Seq("b", "a")), ""))
    assertNotEquals(fingerprint8(Items(Seq("ab", "c")), ""), fingerprint8(Items(Seq("a", "bc")), ""))
