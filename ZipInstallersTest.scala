//| scalaVersion: 3.10.0
//| extends: mill.script.ScalaModule.Raw
//| moduleDeps: [PublishZipInstallers.scala]

import java.util.jar.{Attributes, JarEntry, JarOutputStream, Manifest}
import java.util.zip.ZipFile
import scala.util.Using

/** Offline packaging checks with tiny representative runtimes for all five platforms. */
object ZipInstallersTest:
  private def fails(body: => Any): Unit =
    val failed = try
      body
      false
    catch case _: IllegalArgumentException => true
    assert(failed, "Expected invalid packaging input to fail")

  private def makeJar(path: os.Path, version: String): Unit =
    val manifest = new Manifest()
    manifest.getMainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
    manifest.getMainAttributes.putValue("Implementation-Version", version)
    Using.resource(new JarOutputStream(os.write.outputStream(path), manifest)) { jar =>
      for name <- Seq("fdswarm/FdSwarm.class", "FDSwarmDocs/index.html") do
        jar.putNextEntry(new JarEntry(name))
        jar.write("fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8))
        jar.closeEntry()
    }

  def main(args: Array[String]): Unit =
    val directory = os.temp.dir(prefix = "fdswarm-zip-tests-")
    try
      val jar = directory / "fdswarm.jar"
      makeJar(jar, "0.0.4-30")
      val runtimeRoot = directory / "runtime images"
      for platform <- BuildZipInstallers.platforms do
        val home = runtimeRoot / platform / "jdk" / "Contents" / "Home"
        val executable = home / "bin" / (if platform.startsWith("windows-") then "java.exe" else "java")
        os.write(executable, "runtime fixture", createFolders = true)
        os.write(home / "lib" / "modules", "JavaFX fixture", createFolders = true)
        os.perms.set(executable, "rwxr-xr-x")
      val repo = directory / "repository"
      os.write(repo / "application.conf", "configuration", createFolders = true)
      val output = directory / "artifacts"
      os.makeDir.all(output)
      os.write(output / "keep.txt", "unrelated artifact")
      val zips = BuildZipInstallers.build(jar, runtimeRoot, output, repo)
      assert(zips.size == 5)
      for (zip, platform) <- zips.zip(BuildZipInstallers.platforms) do
        assert(zip.last == s"FdSwarm-0.0.4-30-$platform.zip")
        Using.resource(new ZipFile(zip.toIO)) { file =>
          def contents(name: String): Array[Byte] =
            Using.resource(file.getInputStream(file.getEntry(s"FdSwarm/$name")))(_.readAllBytes())
          assert(java.util.Arrays.equals(contents("lib/fdswarm.jar"), os.read.bytes(jar)))
          assert(new String(contents("conf/application.conf")) == "configuration")
          assert(file.getEntry("FdSwarm/runtime/lib/modules") != null)
          if platform.startsWith("windows-") then
            assert(new String(contents("bin/fdswarm.bat")).contains("lib\\fdswarm.jar"))
            assert(file.getEntry("FdSwarm/bin/install-start-menu-shortcut.bat") != null)
          else
            val launcher = new String(contents("bin/fdswarm"))
            assert(launcher.contains(s"EXPECTED_PLATFORM=\"$platform\""))
            assert(launcher.contains("$ACTUAL_PLATFORM") && launcher.contains("lib/fdswarm.jar"))
        }
      assert(os.read(output / "keep.txt") == "unrelated artifact")
      val extracted = directory / "extracted"
      os.makeDir.all(extracted)
      os.proc("unzip", "-q", zips(2).toString, "-d", extracted.toString).call()
      val launcher = extracted / "FdSwarm" / "bin" / "fdswarm"
      assert(java.nio.file.Files.isExecutable(launcher.toNIO))
      os.proc("sh", "-n", launcher.toString).call()
      assert(BuildZipInstallers.version(jar) == "0.0.4-30")
      val snapshot = directory / "snapshot.jar"
      makeJar(snapshot, "0.0.4-SNAPSHOT")
      fails(BuildZipInstallers.version(snapshot))
      fails(BuildZipInstallers.runtimes(directory / "missing runtimes"))
      fails(PublishZipInstallers.publish("v0.0.4-31", zips))
      println("ZIP checks passed: all five layouts, exact JAR, runtime, config, launchers, permissions, and invalid inputs.")
    finally os.remove.all(directory)
