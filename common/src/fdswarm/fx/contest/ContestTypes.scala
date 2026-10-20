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
      id = contest.getString("name"),
      name = contest.getString("displayName"),
      compute = ContestDateRule(Some(contest.getConfig("dates"))),
      cabrilloContestName = contest.getString("cabrilloContestName"),
      allowChosing = !contest.hasPath("allowChosing") || contest.getBoolean("allowChosing")
    )
  }
  require(values.forall(ct => ct.id.nonEmpty && ct.id != "NONE"), "Contest IDs must be nonempty and cannot be NONE")
  require(values.map(_.id).distinct.size == values.size, "Duplicate contest IDs")
  // Validate the date rules at startup, rather than when a UI first displays them.
  values.foreach(_.compute(2027))

  private val byId = (values :+ ContestType.NONE).map(ct => ct.id -> ct).toMap
  def get(id: String): Option[ContestType] = byId.get(id)
  def apply(id: String): ContestType =
    get(id).getOrElse(throw new IllegalArgumentException(s"Unknown contest: $id"))

  val codec: Codec[ContestType] = Codec.from(
    Decoder.decodeString.emap(id => get(id).toRight(s"Invalid ContestType: $id")),
    Encoder.encodeString.contramap(_.id)
  )
