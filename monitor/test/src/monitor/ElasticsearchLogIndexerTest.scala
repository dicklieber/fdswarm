package monitor

import fdswarm.util.NodeIdentity
import _root_.io.circe.parser.parse

class ElasticsearchLogIndexerTest extends munit.FunSuite:
  private val nodeIdentity = NodeIdentity(
    hostIp = "127.0.0.1",
    port = 8080,
    hostName = "alpha",
    instanceId = "alpha-id"
  )

  test("documentForLine adds node using short host"):
    val document = ElasticsearchLogIndexer.documentForLine("""{"message":"started"}""", nodeIdentity)

    val fields = parse(document).toOption.flatMap(_.asObject).get
    assertEquals(fields("message").flatMap(_.asString), Some("started"))
    assertEquals(fields("node").flatMap(_.asString), Some("alpha:8080"))

  test("documentForLine leaves non-object log lines unchanged"):
    val line = "not json"

    assertEquals(ElasticsearchLogIndexer.documentForLine(line, nodeIdentity), line)
