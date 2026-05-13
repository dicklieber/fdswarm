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
 *
 */

package fdswarm.store

import fdswarm.{Ports, StartupInfo}
import fdswarm.contestStart.ContestStartManager
import fdswarm.io.FileHelper
import fdswarm.logging.{LazyStructuredLogging, Locus}
import fdswarm.model.*
import fdswarm.replication.*
import fdswarm.scoring.ContestScoringService
import fdswarm.util.Ids.Id
import fdswarm.util.{NodeIdentity, NodeIdentityManager, StatsSource}
import io.circe.generic.auto.deriveDecoder
import io.circe.parser.decode
import jakarta.inject.*
import javafx.application.Platform
import os.Path
import scalafx.collections.ObservableBuffer

import java.time.Instant
import scala.collection.concurrent.TrieMap

@Singleton
class QsoStore @Inject() (
    fileHelper: FileHelper,
    transport: Transport,
    startupInfo: StartupInfo,
    contestStartManager: ContestStartManager,
    filenameStamp: fdswarm.util.FilenameStamp,
    localNodeStatus: LocalNodeStatus,
    contestScoringService: ContestScoringService,
    nodeStatusDispatcher: NodeStatusDispatcher
) extends StatsSource(Locus.Qso) with LazyStructuredLogging():

  val qsoCollection: ObservableBuffer[Qso] = new ObservableBuffer[Qso]()
  protected val map: TrieMap[Id, Qso] = new TrieMap
  private val path = fileHelper.directory / "qsosJournal.json"
  private val archiveDirectory: Path = fileHelper.directory / "qsosJournal.archive"
  // Metrics
  private val qsoEntryCounter = addCounter("qsoEntry")
  private val qsoEnteredMeter = addMeter("entered")
  private val qsoReplicatedUdpMeter = addMeter("replicated.udp")
  private val qsoReplicatedAllQsosMeter = addMeter("replicated.allQsos")
  private val hashCalculatorTimer = addTimer("hashCalculation")
  private val qsoCollectionSizeGauge = addGauge("qsoCollectionSize")(map.size)

  def add(qso: Qso): StyledMessage = addSingle(qso = qso, markLocalEntry = true, broadcast = true)

  private def addSingle(qso: Qso, markLocalEntry: Boolean, broadcast: Boolean): StyledMessage =
    val isDuplicateInStore = map.values
      .exists(existing => existing.dupCriterion == qso.dupCriterion)
    if isDuplicateInStore then StyledMessage(qso.rejectedMsg, "duplicate-qso")
    else
      if markLocalEntry then
        qsoEntryCounter.inc()
        qsoEnteredMeter.mark()
      val jsonString = qso.asJsonCompact
      os.write.append(path, jsonString + "\n", createFolders = true)
      addToMap(qso)
      mutateQsoCollection(qsoCollection.prepend(qso))
      calculateStats()
      if broadcast then
        val bytes: Array[Byte] = jsonString.getBytes("UTF-8")
        transport.send(Service.QSO, bytes)
      StyledMessage(s"Added ${qso.dupCriterion} to store", "addQsoOk")
  nodeStatusDispatcher.addListener(service = Service.QSO) { (_, qso) =>
    val styledMessage = addSingle(qso = qso, markLocalEntry = false, broadcast = false)
    if styledMessage.css == "addQsoOk" then qsoReplicatedUdpMeter.mark()
  }

  private def addToMap(qso: Qso): Unit =
    val uuid = qso.uuid
    val maybeQso = map.putIfAbsent(uuid, qso)
    maybeQso.foreach(was => logger.error(s"Was already a qso for uuid: $uuid $qso"))

  def add(batch: Seq[Qso]): Unit = addBatch(batch = batch, markLocalEntries = true)

  private def addBatch(batch: Seq[Qso], markLocalEntries: Boolean): Unit = {
    val toAdd = batch.filter { qso =>
      val uuid = qso.uuid
      map.putIfAbsent(uuid, qso).isEmpty
    }

    if toAdd.nonEmpty then
      val lines = toAdd.map(_.asJsonCompact + "\n").mkString
      os.write.append(path, lines, createFolders = true)
      mutateQsoCollection(qsoCollection.prependAll(toAdd))
      if markLocalEntries then
        qsoEntryCounter.inc(toAdd.size.toLong)
        qsoEnteredMeter.mark(toAdd.size.toLong)
      else qsoReplicatedAllQsosMeter.mark(toAdd.size.toLong)
      calculateStats()

    val added = toAdd.size
    val received = batch.size
    val dups = received - added
    val storeStats = localNodeStatus.statusMessage.storeStats
    logger.info("batch",
      "received" -> received,
      "added" -> added,
      "dups" -> dups,
      "newCount" -> storeStats.qsoCount
    )
  }

  def addReplicated(batch: Seq[Qso]): Unit = addBatch(batch = batch, markLocalEntries = false)

  def get(uuid: Id): Option[Qso] = map.get(uuid)

  def hasQsos: Boolean = map.nonEmpty

  def potentialDups(startOfCallsign: String, _bandmode: BandMode): DupInfo =
    val allPotentialDupCallsigns = QsoStore.potentialDupCallsigns(
      callsigns = map.valuesIterator.map(_.callsign),
      startOfCallsign = startOfCallsign
    )
    val frustNDups = allPotentialDupCallsigns.take(70)
    DupInfo(frustNDups, allPotentialDupCallsigns.size)

  def archiveAndClear(): Unit =
    os.makeDir.all(archiveDirectory)
    val timestampedFile = archiveDirectory / s"${filenameStamp.build()}.qsosJournal.json"
    if os.isFile(path) then
      os.move(path, timestampedFile)
      logger.info(s"Archived QSOs to $timestampedFile")
    else if os.exists(path) then
      logger.error("Cannot archive QSO journal because path is not a file", "path" -> path.toString)

    map.clear()

    mutateQsoCollection(qsoCollection.clear())
    calculateStats()

  private def calculateStats(): Unit =
    refreshScores()
    val localNodeIdentity = NodeIdentityManager.nodeIdentity
    val (qsoIds, ourQsoCount) = map.valuesIterator.foldLeft((List.empty[Id], 0)) {
      case ((ids, count), qso) =>
        val nextCount =
          if QsoStore.isLocalNodeQso(qso = qso, localNodeIdentity = localNodeIdentity) then count + 1
          else count
        (qso.uuid :: ids, nextCount)
    }
    val idsHash =
      val context = hashCalculatorTimer.time()
      try fdswarm.replication.calcShaHash(qsoIds)
      finally context.stop()
    localNodeStatus.updateStoreStats(
      StoreStats(
        hash = idsHash,
        qsoCount = map.size,
        ourQsoCount = ourQsoCount
      )
    )

  private def refreshScores(): Unit = contestScoringService.refresh(qsos = all)

  if startupInfo.info.exists(_.clearQsos) then
    logger.info("StartupInfo Clearing QSOs journal")
    if os.isFile(path) then os.remove(path) else if os.isDir(path) then os.remove.all(path)

  contestStartManager.contestStart.onChange((_, oldContestStart, nextContestStart) =>
    if nextContestStart.start.isAfter(oldContestStart.start) then
      logger.info("event" -> "ContestStart", "contestStart" -> nextContestStart.start)

      removeOlderThanAndRewrite(cutoff = nextContestStart.start)
  )

  if os.isFile(path) then
    val cutoff = activeContestStart
    var loaded = 0
    var ignoredOlderThanContestStart = 0
    os.read.lines(path).iterator.map(_.trim).filter(_.nonEmpty).foreach { line =>
      decode[Qso](line) match
        case Right(qso) =>
          if QsoStore.isBeforeContestStart(qso, cutoff) then ignoredOlderThanContestStart += 1
          else
            Ports.port(qso.qsoMetadata.node)
            if map.putIfAbsent(qso.uuid, qso).isEmpty then
              loaded += 1
              mutateQsoCollection(qsoCollection.prepend(qso))
        case Left(error) => logger.error(s"Failed to decode Qso from line: $line", error)
    }
    logger.info("qso-journal-load",
      "contestStart" -> cutoff,
      "loaded" -> loaded,
      "ignoredOlderThanContestStart" -> ignoredOlderThanContestStart
    )
    calculateStats()
  else if os.exists(path) then
    logger.error("QSO journal path exists but is not a regular file", "path" -> path.toString)

  /**
    * Thread-safe snapshot of all QSOs, sorted by stamp. Prefer this over reading `qsoCollection`
    * from non-JavaFX threads (e.g., Cask routes).
    */
  def all: Seq[Qso] = map.values.toSeq.sorted

  private def mutateQsoCollection(mutation: => Unit): Unit =
    if Platform.isFxApplicationThread then mutation else Platform.runLater(() => mutation)

  def size: Int = map.size

  private def activeContestStart: Instant = contestStartManager.contestStart.value.start

  private def removeOlderThanAndRewrite(cutoff: Instant): Unit =
    val currentQsos = map.values.toSeq
    val (removed, kept) = currentQsos.partition(_.stamp.isBefore(cutoff))
    if removed.nonEmpty then
      val timestampedFile = archiveDirectory / s"${filenameStamp.build()}.qsosJournal.json"
      if os.isFile(path) then os.move(path, timestampedFile)
      val orderedKept = kept.sorted
      val lines = orderedKept.map(_.asJsonCompact + "\n").mkString
      os.write.over(path, lines, createFolders = true)
      map.clear()
      orderedKept.foreach(qso => map.put(qso.uuid, qso))
      mutateQsoCollection {
        qsoCollection.clear()
        orderedKept.foreach(qso => qsoCollection.prepend(qso))
      }
      logger.info("qso-journal-prune-for-contest-start",
        "contestStart" -> cutoff,
        "removed" -> removed.size,
        "kept" -> orderedKept.size,
        "archivedTo" -> timestampedFile.toString
      )
      calculateStats()
    else
      logger.info(
        "event" -> "qso-journal-prune-for-contest-start",
        "contestStart" -> cutoff,
        "removed" -> 0,
        "kept" -> currentQsos.size
      )

object QsoStore:
  private[store] def isBeforeContestStart(qso: Qso, contestStart: Instant): Boolean =
    qso.stamp.isBefore(contestStart)

  private[store] def isLocalNodeQso(qso: Qso, localNodeIdentity: NodeIdentity): Boolean =
    val qsoNode = qso.qsoMetadata.node
    qsoNode == localNodeIdentity ||
      qsoNode.hostName == localNodeIdentity.hostName &&
        qsoNode.port == localNodeIdentity.port

  private[store] def sameBandMode(left: BandMode, right: BandMode): Boolean =
    left.band == right.band && left.mode == right.mode

  private[store] def potentialDupCallsigns(
      callsigns: IterableOnce[Callsign],
      startOfCallsign: String
  ): Seq[Callsign] =
    val normalizedStart = startOfCallsign.trim.toUpperCase
    callsigns.iterator.filter(_.startsWith(normalizedStart)).toSet.toSeq.sortBy(_.value)
