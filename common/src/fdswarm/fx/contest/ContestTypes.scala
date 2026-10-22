package fdswarm.fx.contest

import com.typesafe.config.{ConfigFactory, ConfigParseOptions}
import io.circe.Codec
import jakarta.inject.{Inject, Singleton}

/** Contest definitions are loaded from bundled JSON resources. */
@Singleton
final class ContestTypes @Inject()():
  private[contest] val definitions = Seq("ARRL", "WFD").map { name =>
    ConfigFactory.parseResources(
      s"contestTypes/$name.json",
      ConfigParseOptions.defaults().setAllowMissing(false)
    ).resolve()
  }

  val values: Seq[ContestType] = definitions.map { contest =>
    ContestType(
      name = contest.getString("name"),
      displayName = contest.getString("displayName"),
      compute = ContestDateRule(contest.getConfig("dates")),
      cabrilloContestName = contest.getString("cabrilloContestName")
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

  val codec: Codec[ContestType] = summon[Codec[ContestType]]

object ContestTypes:
  /** Shared with dependency injection so derived codecs use the actual application definitions. */
  lazy val configured: ContestTypes = new ContestTypes()
