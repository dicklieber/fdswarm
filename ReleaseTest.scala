//| scalaVersion: 3.10.0
//| extends: mill.script.ScalaModule.Raw
//| moduleDeps: [Release.scala]

object ReleaseTest:
  def main(args: Array[String]): Unit =
    for mac <- Seq(false, true); windows <- Seq(false, true); zips <- Seq(false, true) do
      val answers = Iterator(mac, windows, zips).map(if _ then "yes" else "no")
      val prompts = scala.collection.mutable.ArrayBuffer.empty[String]
      val selected = Release.publishing { label =>
        prompts += label
        answers.next()
      }
      assert(selected == Release.Publishing(mac, windows, zips))
      assert(prompts.size == 3 && prompts.head.contains("macOS") && prompts(1).contains("Windows") && prompts(2).contains("ZIP"))
    assert(Release.publishing(_ => "") == Release.Publishing(true, true, true))
    val retried = Iterator("invalid", "n", "n", "y")
    assert(Release.publishing(_ => retried.next()) == Release.Publishing(false, false, true))
    val closed = try
      Release.publishing(_ => null)
      false
    catch case _: IllegalArgumentException => true
    assert(closed)
    println("Release selection checks passed: all eight combinations, defaults, invalid input, and closed input.")
    runtimeChecks()

  private def runtimeChecks(): Unit =
    val work = os.temp.dir(prefix = "fdswarm-runtime-preflight-")
    try
      val root = work / "runtime images"
      def install(platform: String): Unit =
        val name = if platform.startsWith("windows-") then "java.exe" else "java"
        os.write(root / platform / "jdk" / "bin" / name, platform, createFolders = true)
      var fetches = 0
      def fetch(): Unit =
        fetches += 1
        BuildZipInstallers.missingRuntimes(root).foreach(install)
      assert(!Release.prepareZipRuntimes(root, _ => "no", () => fetch()))
      assert(!Release.prepareZipRuntimes(root, _ => "", () => fetch()))
      assert(!Release.prepareZipRuntimes(root, _ => null, () => fetch()))
      assert(fetches == 0 && !os.exists(root))
      assert(Release.prepareZipRuntimes(root, label => {
        assert(label.contains("large download") && label.contains("[y/N]"))
        "yes"
      }, () => fetch()))
      assert(fetches == 1 && BuildZipInstallers.missingRuntimes(root).isEmpty)
      assert(Release.prepareZipRuntimes(root, _ => sys.error("Unexpected download prompt"),
        () => sys.error("Unexpected fetch")))

      // Exercise the real fetch script offline, with a curl substitute that copies a fixture archive.
      val bin = work / "bin"
      val archive = work / "runtime.tar.gz"
      val fixture = work / "fixture"
      os.write(fixture / "jdk" / "bin" / "java", "downloaded runtime", createFolders = true)
      os.proc("tar", "-czf", archive.toString, "-C", fixture.toString, ".").call()
      os.write(bin / "curl", """#!/usr/bin/env bash
set -eu
echo call >> "$TEST_CURL_LOG"
if [[ "$1" == -fsSL ]]; then
  echo https://example.test/runtime.tar.gz
else
  while [[ "$1" != --output ]]; do shift; done
  cp "$TEST_ARCHIVE" "$2"
fi
""", createFolders = true)
      os.perms.set(bin / "curl", "rwxr-xr-x")
      val log = work / "curl.log"
      def runFetch(): Unit =
        os.proc("bash", "scripts/fetch-liberica-runtimes.sh", "--dest", root.toString, "--missing-only").call(
          env = Map("PATH" -> s"$bin:${sys.env("PATH")}", "TEST_CURL_LOG" -> log.toString,
            "TEST_ARCHIVE" -> archive.toString), stdout = os.Pipe)
        ()
      runFetch()
      assert(!os.exists(log)) // Cached runtimes must not even query BellSoft.
      os.remove.all(root / "linux-aarch64")
      assert(BuildZipInstallers.missingRuntimes(root) == Seq("linux-aarch64"))
      assert(Release.prepareZipRuntimes(root, _ => "yes", () => runFetch()))
      assert(os.read.lines(log).size == 2) // One URL lookup and one archive download.
      assert(os.read(root / "windows-x64" / "jdk" / "bin" / "java.exe") == "windows-x64")
      assert(os.read(root / "linux-aarch64" / "jdk" / "bin" / "java") == "downloaded runtime")
      os.remove.all(root / "linux-aarch64")
      val incomplete = try
        Release.prepareZipRuntimes(root, _ => "yes", () => ())
        false
      catch case _: IllegalArgumentException => true
      assert(incomplete)
      println("Runtime preflight checks passed: decline, cached reuse, fetch consent, partial downloads, and incomplete fetch rejection.")
    finally os.remove.all(work)
