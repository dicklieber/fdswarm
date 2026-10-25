package fdswarm.api

import fdswarm.{IntegrationTestConfig, StartupConfig, StartupInfo}
import fdswarm.model.{BandMode, Callsign}
import io.circe.syntax.*
import munit.FunSuite

class IntegrationTestEndpointsTest extends FunSuite:
  private def startup(enabled: Boolean)(run: StartupInfo => Unit): Unit =
    val path = os.temp(prefix = "fdswarm-startup-", deleteOnExit = false)
    try
      val config = StartupConfig(operator = Callsign("W1AW"), bandMode = BandMode("20m CW"),
        integrationTest = Some(IntegrationTestConfig("/private/tmp/fdswarm-test", 18090, "a" * 32)))
      os.write.over(path, config.asJson.noSpaces)
      val args = Array("--startupInfo", path.toString) ++ (if enabled then Array("--integration-test") else Array.empty[String])
      run(new StartupInfo(args))
    finally os.remove(path)

  private def endpoints(info: StartupInfo) =
    new IntegrationTestEndpoints(info, null, null, null, null, null, null)

  test("startup JSON alone cannot enable integration controls"):
    startup(false) { info =>
      assertEquals(info.integrationTest, None)
      assertEquals(endpoints(info).endpoints, Nil)
      assert(!endpoints(info).authorized("a" * 32))
    }

  test("explicit flag enables only the configured token"):
    startup(true) { info =>
      assertEquals(endpoints(info).endpoints.size, 4)
      assert(endpoints(info).authorized("a" * 32))
      assert(!endpoints(info).authorized("b" * 32))
    }

  test("integration flag requires startup settings"):
    intercept[IllegalArgumentException](new StartupInfo(Array("--integration-test")))
