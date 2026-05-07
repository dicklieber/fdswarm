package monitor

import fdswarm.logging.LazyStructuredLogging
import fdswarm.util.NodeIdentity
import jakarta.inject.{Inject, Singleton}

import java.net.URI
import java.net.http.{HttpClient, HttpHeaders, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.time.Duration
import scala.util.{Failure, Try}

@Singleton
final class NodeLogScraper @Inject()(elasticsearchLogIndexer: ElasticsearchLogIndexer) extends LazyStructuredLogging:

  private val httpClient: HttpClient =
    HttpClient
      .newBuilder()
      .connectTimeout(Duration.ofSeconds(5))
      .build()

  /**
   * Scrape log data from a node, index it in Elasticsearch, and return the next log offset.
   *
   * @param nodeIdentity which node to scrape.
   * @param offset where in the log to start scraping.
   */
  def scrapeNode(nodeIdentity: NodeIdentity, offset: Long): Try[IndexOperation] =
    Try{
      val logUri = nodeLogUri(nodeIdentity, offset)
      val request = HttpRequest.newBuilder(logUri).timeout(Duration.ofSeconds(10)).GET().build()

      val response: HttpResponse[Array[Byte]] = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
      val code = response.statusCode()
      val body = response.body()
      val headers: HttpHeaders = response.headers()
      logger.debug(s"Scraped node $nodeIdentity, status code $code, headers $headers")
      val logIndexResult: LogIndexResult = elasticsearchLogIndexer.indexLog(body, nodeIdentity)
      if logIndexResult.indexedLines > 0 then
        logger.info(s"Indexed ${logIndexResult.indexedLines} lines from node $nodeIdentity")
      val logTo = longHeader(response, "X-Log-To").fold(message => throw IllegalStateException(message), identity)

      IndexOperation(itemCount = logIndexResult.indexedLines, offset = logTo)
    }



  private def failed(message: String): Try[IndexOperation] =
    Failure(IllegalStateException(message))

  private def nodeLogUri(nodeIdentity: NodeIdentity, fromByte: Long): URI =
    URI.create(s"http://${nodeIdentity.hostIp}:${nodeIdentity.port}/log?fromByte=$fromByte")

  private def metadataFrom(response: HttpResponse[Array[Byte]]): Either[String, LogApiMetadata] =
    for
      from <- longHeader(response, "X-Log-From")
      to <- longHeader(response, "X-Log-To")
      size <- longHeader(response, "X-Log-Size")
      truncated <- booleanHeader(response, "X-Log-Truncated")
      logId <- stringHeader(response, "X-Log-Id")
    yield LogApiMetadata(from, to, size, logId, truncated)

  private def stringHeader(response: HttpResponse[?], name: String): Either[String, String] =
    val value = response.headers().firstValue(name)
    if value.isPresent then Right(value.get())
    else Left(s"Missing $name header.")

  private def longHeader(response: HttpResponse[?], name: String): Either[String, Long] =
    stringHeader(response, name).flatMap(value =>
      Try(value.toLong).toEither.left.map(_ => s"Invalid $name header: $value")
    )

  private def booleanHeader(response: HttpResponse[?], name: String): Either[String, Boolean] =
    stringHeader(response, name).flatMap(value =>
      value.toLowerCase match
        case "true"  => Right(true)
        case "false" => Right(false)
        case _       => Left(s"Invalid $name header: $value")
    )

  private def utf8(bytes: Array[Byte]): String =
    new String(bytes, StandardCharsets.UTF_8)
