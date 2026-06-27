/*
 * Copyright (c) 2026. Dick Lieber, WA9NNN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package fdswarm.importer

import fdswarm.StationConfigManager
import fdswarm.fx.contest.ContestConfigManager
import fdswarm.logging.LazyStructuredLogging
import fdswarm.model.QsoMetadata
import fdswarm.store.QsoStore
import fdswarm.util.NodeIdentityManager
import jakarta.inject.{Inject, Singleton}

import java.net.{DatagramPacket, InetAddress, InetSocketAddress, MulticastSocket}
import java.nio.{ByteBuffer, ByteOrder}

/** Listens for WSJT-X UDP multicast messages and processes type 12 (ADIF_LOG_QSO) packets.
  *
  * WSJT-X broadcasts a binary UDP message each time a QSO is logged.  Message type 12
  * carries the complete ADIF record for the just-logged contact.  This listener joins the
  * configured multicast group, decodes those packets, and adds the resulting
  * [[fdswarm.model.Qso]] objects to the [[QsoStore]] using the live station/contest metadata.
  *
  * Configuration is managed by [[WsjtxConfigManager]] (persisted to `wsjtx.json`,
  * falling back to `fdswarm.wsjtx.*` in `reference.conf`).  Call [[restart]] after
  * saving new config to apply changes immediately.
  *
  * Wire protocol (big-endian):
  *   - uint32  magic  = 0xADBCCBDA
  *   - uint32  schema (ignored)
  *   - uint32  type   = 12 for ADIF_LOG_QSO
  *   - utf8str id     (length-prefixed; 0xFFFFFFFF = null)
  *   - utf8str adif   (length-prefixed ADIF record text)
  */
@Singleton
class WsjtxListener @Inject() (
    wsjtxConfigManager: WsjtxConfigManager,
    qsoStore: QsoStore,
    stationConfigManager: StationConfigManager,
    contestConfigManager: ContestConfigManager
) extends LazyStructuredLogging():

  private val WsjtxMagic      = 0xADBCCBDAL
  private val MessageTypeAdif = 12

  @volatile private var currentSocket: Option[MulticastSocket] = None
  @volatile private var currentThread: Option[Thread]          = None

  startIfEnabled()

  /** Stops any running listener and restarts according to the current [[WsjtxConfigManager]] state. */
  def restart(): Unit =
    stop()
    startIfEnabled()

  private def startIfEnabled(): Unit =
    val cfg = wsjtxConfigManager.wsjtxConfig.value
    if cfg.enabled then
      val t = new Thread(() => run(cfg), "WsjtxListener")
      t.setDaemon(true)
      t.start()
      currentThread = Some(t)
      logger.info("WsjtxListener started", "group" -> cfg.multicastGroup, "port" -> cfg.port)
    else
      logger.info("WsjtxListener disabled")

  private def stop(): Unit =
    currentThread.foreach(_.interrupt())
    currentSocket.foreach(_.close())
    currentSocket = None
    currentThread = None

  private def run(cfg: WsjtxConfig): Unit =
    try
      val group  = InetAddress.getByName(cfg.multicastGroup)
      val socket = new MulticastSocket(cfg.port)
      currentSocket = Some(socket)
      socket.joinGroup(new InetSocketAddress(group, cfg.port), null)
      logger.debug("WsjtxListener joined multicast group", "group" -> cfg.multicastGroup, "port" -> cfg.port)
      val buf = new Array[Byte](65535)
      while !Thread.currentThread().isInterrupted && !socket.isClosed do
        try
          val packet = new DatagramPacket(buf, buf.length)
          socket.receive(packet)
          processPacket(packet.getData.take(packet.getLength))
        catch
          case _: java.net.SocketException if Thread.currentThread().isInterrupted || socket.isClosed =>
            // normal shutdown
          case e: Exception =>
            logger.error("Error receiving WSJT-X packet", e)
    catch
      case e: Exception =>
        if !Thread.currentThread().isInterrupted then
          logger.error("WsjtxListener failed to start", e)
    finally
      currentSocket = None
      currentThread = None

  private def processPacket(data: Array[Byte]): Unit =
    val bb = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
    if bb.remaining() < 12 then return

    val magic = Integer.toUnsignedLong(bb.getInt())
    if magic != WsjtxMagic then return

    bb.getInt()  // schema version – ignored
    val msgType = bb.getInt()

    if msgType == MessageTypeAdif then
      skipUtf8String(bb)  // ID field – not needed
      val adif = readUtf8String(bb)
      if adif.nonEmpty then
        logger.debug("Received WSJT-X ADIF message", "adif" -> adif)
        processAdif(adif)

  private def processAdif(adifRecord: String): Unit =
    given QsoMetadata = QsoMetadata(
      station = stationConfigManager.stationConfig,
      node    = NodeIdentityManager.nodeIdentity,
      contest = contestConfigManager.contestType
    )
    AdifRecordParser().parseRecord(adifRecord) match
      case scala.util.Success(qso) =>
        val msg = qsoStore.add(qso)
        logger.info("WSJT-X QSO added", "callsign" -> qso.callsign.value, "result" -> msg.text)
      case scala.util.Failure(ex) =>
        logger.warn("Failed to parse WSJT-X ADIF record", "record" -> adifRecord, "error" -> ex.getMessage)

  /** Reads a length-prefixed UTF-8 string; returns "" for null or empty strings. */
  private def readUtf8String(bb: ByteBuffer): String =
    if bb.remaining() < 4 then return ""
    val len = bb.getInt()
    if len == -1 || len == 0 then return ""
    if bb.remaining() < len then return ""
    val bytes = new Array[Byte](len)
    bb.get(bytes)
    new String(bytes, "UTF-8")

  /** Skips a length-prefixed UTF-8 string in the buffer. */
  private def skipUtf8String(bb: ByteBuffer): Unit =
    if bb.remaining() < 4 then return
    val len = bb.getInt()
    if len > 0 && bb.remaining() >= len then bb.position(bb.position() + len)
