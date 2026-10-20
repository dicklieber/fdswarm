package fdswarm

import com.typesafe.config.ConfigFactory
import fdswarm.fx.contest.ContestType
import munit.FunSuite

import java.time.{Duration, ZonedDateTime}

class ContestDateCalculatorTest extends FunSuite:
  test("WFD 2027 uses the fourth full weekend and the 30-hour operational period"):
    val dates = ContestDateCalculator.wfd(2027)
    assertEquals(dates.startUtc, ZonedDateTime.parse("2027-01-23T16:00Z"))
    assertEquals(dates.endUtc, ZonedDateTime.parse("2027-01-24T21:59Z"))
    assertEquals(Duration.between(dates.startUtc, dates.endUtc.plusMinutes(1)), Duration.ofHours(30))
    assertEquals(
      dates.setup,
      Some(ContestSetup(ZonedDateTime.parse("2027-01-22T16:00Z"), Duration.ofHours(12)))
    )

  test("WFD selects the fourth weekend even when January has five full weekends"):
    val dates = ContestDateCalculator.wfd(2021)
    assertEquals(dates.startUtc, ZonedDateTime.parse("2021-01-23T16:00Z"))
    assertEquals(dates.endUtc, ZonedDateTime.parse("2021-01-24T21:59Z"))

  test("a Sunday on January 1 does not count as a full weekend"):
    val dates = ContestDateCalculator.wfd(2023)
    assertEquals(dates.startUtc, ZonedDateTime.parse("2023-01-28T16:00Z"))
    assertEquals(dates.endUtc, ZonedDateTime.parse("2023-01-29T21:59Z"))

  test("ARRL retains its fourth June weekend and existing operational times"):
    val dates = ContestDateCalculator.arrl(2027)
    assertEquals(dates.startUtc, ZonedDateTime.parse("2027-06-26T18:00Z"))
    assertEquals(dates.endUtc, ZonedDateTime.parse("2027-06-27T20:59Z"))
    assertEquals(dates.setup, None)

  test("date calculations honor configuration overrides"):
    val config = ConfigFactory.parseString("""
      fdswarm.contestDates.WFD {
        month = 2
        fullWeekend = 2
        startUtc = "15:00"
        endUtc = "20:59"
        setup {
          daysFromSaturday = -2
          startUtc = "14:00"
          maxCumulativeDuration = 10h
        }
      }
    """).withFallback(ConfigFactory.load()).resolve()
    val dates = ContestDateCalculator.configuredDates(2027, "WFD", config)
    assertEquals(dates.startUtc, ZonedDateTime.parse("2027-02-13T15:00Z"))
    assertEquals(dates.endUtc, ZonedDateTime.parse("2027-02-14T20:59Z"))
    assertEquals(
      dates.setup,
      Some(ContestSetup(ZonedDateTime.parse("2027-02-11T14:00Z"), Duration.ofHours(10)))
    )

  test("contest types use configured dates and NONE retains epoch dates"):
    assertEquals(ContestType.WFD.dates(ZonedDateTime.parse("2027-01-01T00:00Z")), ContestDateCalculator.wfd(2027))
    assertEquals(ContestType.ARRL.dates(ZonedDateTime.parse("2027-01-01T00:00Z")), ContestDateCalculator.arrl(2027))
    assertEquals(ContestDateCalculator.none(2027), ContestDateCalculator.Epoch)
