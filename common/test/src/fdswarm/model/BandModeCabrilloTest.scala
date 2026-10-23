package fdswarm.model

import munit.FunSuite

class BandModeCabrilloTest extends FunSuite:
  test("every supported band has a nonempty Cabrillo frequency or band identifier"):
    assertEquals(BandMode.bandFreqMap.keySet, Band.values.filterNot(_ == Band.OutOfBand).toSet)
    BandMode.bandFreqMap.values.foreach(value => assert(value.nonEmpty))

  test("VHF and microwave bands use Cabrillo band identifiers"):
    val expected = Seq(Band.Band_6m -> "50", Band.Band_4m -> "70", Band.Band_2m -> "144",
      Band.Band_1_25m -> "222", Band.Band_70cm -> "432", Band.Band_33cm -> "902",
      Band.Band_23cm -> "1.2G", Band.Band_13cm -> "2.3G", Band.Band_9cm -> "3.4G",
      Band.Band_6cm -> "5.7G", Band.Band_3cm -> "10G")
    expected.foreach { (band, value) => assertEquals(BandMode.bandToFreq(band), value) }

  test("unrepresentable bands fail instead of silently leaving a blank field"):
    intercept[IllegalArgumentException](BandMode.bandToFreq(Band.OutOfBand))
    intercept[IllegalArgumentException](BandMode.bandToFreq("unknown"))

  test("digital mode is DG in Cabrillo without changing its stored DIGI value"):
    assertEquals(BandMode("20m DIGI").cabMode, "DG")
    assertEquals(BandMode("20m PH").cabMode, "PH")
    assertEquals(BandMode("20m CW").cabMode, "CW")
    assertEquals(BandMode("20m DIGI").toString, "20m DIGI")
