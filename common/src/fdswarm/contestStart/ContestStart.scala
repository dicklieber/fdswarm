package fdswarm.contestStart

import io.circe.Codec

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
  * Qsos with stamp older than this will be ignored
  * @param start when we started the contest.
  */
case class ContestStart(start: Instant = Instant.EPOCH) derives Codec.AsObject:
  override def toString: String =
    if isStarted then
      ContestStart.DateTimeFormat.format(start.atZone(ZoneId.systemDefault()))
    else
      "Contest not started"

  def isStarted: Boolean = start.isAfter(Instant.EPOCH)

object ContestStart:
  private val DateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
