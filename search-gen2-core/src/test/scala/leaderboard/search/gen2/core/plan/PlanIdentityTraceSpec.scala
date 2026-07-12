package leaderboard.search.gen2.core.plan

import org.scalatest.wordspec.AnyWordSpec

final class PlanIdentityTraceSpec extends AnyWordSpec {
  import PlanIdentityFixtures.*

  "PlanIdentityTrace" should {
    "render complete Shape A and Shape B review traces" in {
      val traceA = PlanIdentityTrace.render(viewA.identityOf(planA))
      val traceB = PlanIdentityTrace.render(viewB.identityOf(planB))

      assert(traceA ==
        """contract-fingerprint="inventory-contract-v1"
          |query="  In Stock\n"
          |page-size=25
          |constraint[0]=terms field=department values=["retail","wholesale"]
          |constraint[1]=number-range field=quality bounds=[inclusive("2"),exclusive("5")]
          |constraint[2]=interval-overlap from=availabilityStart to=availabilityEnd bounds=[inclusive("10"),exclusive("20")]
          |sort[0]=field-value field=quality direction=desc
          |facet[0]=terms id=supplier-facet field=supplier size=12 order=key-asc counting=all-applied-hard-filters
          |facet[1]=number-range id=quality-facet field=quality buckets=[half-open(q-low,"0","3"),upper-unbounded(q-high,"3")] counting=all-applied-hard-filters
          |facet[2]=interval-overlap id=availability-facet from=availabilityStart to=availabilityEnd buckets=[half-open(short,"0","10"),upper-unbounded(long,"10")] counting=all-applied-hard-filters
          |group[0]=id=supplier-group key=supplier size=5 representative=fields=[headline,quality] metrics=[best-score(best)] order=[metric(best,desc),matching-document-count(desc),key(asc)] precision=require-exact""".stripMargin
      )
      assert(traceB ==
        """contract-fingerprint="trail-contract-v1"
          |query=absent
          |page-size=20
          |constraint[0]=geo-distance-filter field=coordinates origin="50.1,30.2" radius="2500"
          |signal[0]=geo-proximity field=coordinates origin="50,30"
          |sort[0]=geo-distance field=coordinates origin="50,30" direction=asc
          |facet[0]=terms id=trail-type-facet field=trailType size=8 order=count-desc-then-key-asc counting=all-applied-hard-filters
          |group[0]=id=region-group key=region size=3 representative=identity-only metrics=[min-geo-distance(nearest,coordinates,"50,30")] order=[metric(nearest,asc),key(asc)] precision=allow-approximate""".stripMargin
      )
      assert(!traceA.contains("diagnostic"))
      assert(!traceA.contains("provenance"))
      assert(traceA != PlanIdentityCanonicalEncoding.block(PlanIdentityFixtures.viewA.identityOf(PlanIdentityFixtures.planA)))
    }

    "escape trusted query text without changing its value" in {
      val identity = viewB.identityOf(planB.copy(residualText = Some("a\\b\n\r\t\"c")))
      assert(PlanIdentityTrace.render(identity).contains("query=\"a\\\\b\\n\\r\\t\\\"c\""))
    }
  }
}
