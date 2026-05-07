package monitor

import com.sksamuel.elastic4s.{ElasticClient, ElasticProperties, Response}
import com.sksamuel.elastic4s.ElasticDsl.*
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s.requests.bulk.BulkResponse
import com.typesafe.config.Config
import fdswarm.logging.LazyStructuredLogging
import fdswarm.util.NodeIdentity
import _root_.io.circe.Json
import _root_.io.circe.parser.parse
import jakarta.inject.{Inject, Singleton}

import java.nio.charset.StandardCharsets
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

final case class LogApiMetadata(
    from: Long,
    to: Long,
    size: Long,
    logId: String,
    truncated: Boolean
)

final case class LogIndexResult(attemptedLines: Int, indexedLines: Int, failures: Seq[String]):
  def hasFailures: Boolean = failures.nonEmpty

@Singleton
final class ElasticsearchLogIndexer @Inject()(config: Config) extends LazyStructuredLogging:
  private val elasticsearchUrl: String =
    if config.hasPath("monitor.elasticsearch.url") then config.getString("monitor.elasticsearch.url")
    else "http://localhost:9200"

  private val elasticsearchIndex: String =
    if config.hasPath("monitor.elasticsearch.index") then config.getString("monitor.elasticsearch.index")
    else "fdswarm-logs"

  val client = ElasticClient(JavaClient(ElasticProperties(elasticsearchUrl)))

  def indexLog(logBytes: Array[Byte], nodeIdentity: NodeIdentity): LogIndexResult =

    val payload = new String(logBytes, StandardCharsets.UTF_8)
    val ops = payload
      .split('\n')
      .iterator
      .filter(_.nonEmpty)
      .map(line => indexInto(elasticsearchIndex).doc(ElasticsearchLogIndexer.documentForLine(line, nodeIdentity)))
      .toSeq

    if ops.isEmpty then
      LogIndexResult(attemptedLines = 0, indexedLines = 0, failures = Seq.empty)
    else
      val future: Future[Response[BulkResponse]] = client.execute(bulk(ops))
      val value: Response[BulkResponse] = Await.result(future, 10.seconds)
      value.fold(
        failure =>
          LogIndexResult(
            attemptedLines = ops.size,
            indexedLines = 0,
            failures = Seq(s"bulk request failed with status ${failure.status}: ${failure.error.reason}")
          ),
        response =>
          LogIndexResult(
            attemptedLines = ops.size,
            indexedLines = response.successes.size,
            failures = response.failures.map(item =>
              val reason = item.error.map(_.reason).getOrElse(s"status ${item.status}")
              s"line ${item.itemId + 1}: $reason"
            )
          )
      )

  def deleteElasticsearchIndex(): Unit =
    logger.info(s"Deleting Elasticsearch index: $elasticsearchIndex")
    val future = client.execute(deleteIndex(elasticsearchIndex))
    val response = Await.result(future, 10.seconds)
    response.fold(
      failure => logger.error(s"Failed to delete index $elasticsearchIndex: ${failure.error.reason}"),
      _ => logger.info(s"Successfully deleted index $elasticsearchIndex")
    )

object ElasticsearchLogIndexer:
  private[monitor] def documentForLine(line: String, nodeIdentity: NodeIdentity): String =
    val node = nodeIdentity.shortHost
    parse(line).toOption
      .flatMap(_.asObject)
      .map(jsonObject => Json.fromJsonObject(jsonObject.add("node", Json.fromString(node))).noSpaces)
      .getOrElse(line)
