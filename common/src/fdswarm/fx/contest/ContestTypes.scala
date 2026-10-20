package fdswarm.fx.contest

import com.typesafe.config.Config
import io.circe.{Codec, Decoder, Encoder}
import jakarta.inject.{Inject, Singleton}
import scala.jdk.CollectionConverters.*

/** Contest definitions are loaded once from the application's resolved configuration. */
@Singleton
final class ContestTypes @Inject()(config: Config):
  val values: Seq[ContestType] = config.getConfigList("fdswarm.contests").asScala.toSeq.map { contest =>
    ContestType(
      name = contest.getString("name"),
      displayName = contest.getString("displayName"),
      compute = ContestDateRule(contest.getConfig("dates")),
      cabrilloContestName = contest.getString("cabrilloContestName"),
      allowChosing = !contest.hasPath("allowChosing") || contest.getBoolean("allowChosing")
    )
  }
  require(values.forall(ct => ct.name.nonEmpty), "Contest names must be nonempty")
  require(values.map(_.name).distinct.size == values.size, "Duplicate contest names")
  require(values.nonEmpty, "At least one contest must be configured")
  // Validate the date rules at startup, rather than when a UI first displays them.
  values.foreach(_.compute(2027))

  private val byName = values.map(ct => ct.name -> ct).toMap
  def get(name: String): Option[ContestType] = byName.get(name)
  def apply(name: String): ContestType =
    get(name).getOrElse(throw new IllegalArgumentException(s"Unknown contest: $name"))

  val codec: Codec[ContestType] = Codec.from(
    Decoder.decodeString.emap(name => get(name).toRight(s"Invalid ContestType: $name")),
    Encoder.encodeString.contramap(_.name)
  )

object ContestTypes:
  /** Shared with dependency injection so derived codecs use the actual application definitions. */
  lazy val configured: ContestTypes = new ContestTypes(fdswarm.ApplicationConfig.value)
