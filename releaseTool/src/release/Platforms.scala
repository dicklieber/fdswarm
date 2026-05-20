package release

case class Platform(
    id: String,
    libericaUrl: String
) {

  def isWindows: Boolean =
    id.startsWith("windows")

}

object Platforms {

  val version = "21.0.11+11"

  val all: Seq[Platform] = Seq(
    Platform(
      "windows-x64",
      s"https://download.bell-sw.com/java/$version/bellsoft-jdk$version-windows-amd64-full.zip"
    ),
    Platform(
      "windows-arm64",
      s"https://download.bell-sw.com/java/$version/bellsoft-jdk$version-windows-aarch64-full.zip"
    ),
    Platform(
      "macos-aarch64",
      s"https://download.bell-sw.com/java/$version/bellsoft-jdk$version-macos-aarch64-full.zip"
    ),
    Platform(
      "linux-x64",
      s"https://download.bell-sw.com/java/$version/bellsoft-jdk$version-linux-amd64-full.tar.gz"
    )
  )

}
