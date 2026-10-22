package fdswarm.fx.contest

import io.circe.Codec
import java.util.Base64

/** Compact advertisement; Base64 timestamps must be compared numerically. */
case class ContestConfigVersion(fingerPrint: String, timeStamp: String)
    extends Ordered[ContestConfigVersion]
    derives Codec.AsObject, sttp.tapir.Schema:
  lazy val epochNanos: BigInt = BigInt(Base64.getUrlDecoder.decode(timeStamp))

  override def compare(that: ContestConfigVersion): Int =
    val timeOrder = epochNanos.compare(that.epochNanos)
    if timeOrder != 0 then timeOrder else fingerPrint.compareTo(that.fingerPrint)

  def isNewerThan(local: Option[ContestConfigVersion]): Boolean =
    local.forall(version => fingerPrint != version.fingerPrint && compare(version) > 0)

object ContestConfigVersion:
  def apply(config: ContestConfig): ContestConfigVersion =
    ContestConfigVersion(config.fingerPrint, config.timeStampBase64)
