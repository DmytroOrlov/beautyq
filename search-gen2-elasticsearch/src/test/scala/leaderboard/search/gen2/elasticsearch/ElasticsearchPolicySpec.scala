package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.contract.*
import org.scalatest.wordspec.AnyWordSpec

/** Neutral calibration for the complete Elasticsearch policy algebra (index + query), using the shared
  * book/library document fixture ([[ElasticsearchTestFixtures]]) unrelated to BeautyQ.
  */
final class ElasticsearchPolicySpec extends AnyWordSpec {
  import ElasticsearchTestFixtures.*

  private def policyOrFail(
    weightedFields: Vector[ElasticsearchWeightedTextField[BookDocument]] = queryTextFields,
    geo: Option[ElasticsearchGeoScoringPolicy] = Some(geoScoringPolicy),
  ): ElasticsearchPolicy[BookDocument, String] =
    ElasticsearchPolicy(planContractVersion, policy, weightedFields, ElasticsearchTextOperator.Or, geo, ElasticsearchTotalHitsPolicy.ExactRequired, defaultSortPolicy) match {
      case Right(value) => value
      case Left(errors) => fail(s"expected a valid policy, got ${errors.toVector}")
    }

  "ElasticsearchPolicy" should {
    "construct successfully with the complete accepted fixture choices" in {
      policyOrFail()
    }

    "normalize queryTextFields into document declaration order, preserving each assignment's exact weight and field handle" in {
      val reversed = queryTextFields.reverse

      val policyInOrder  = policyOrFail(queryTextFields)
      val policyReversed = policyOrFail(reversed)

      assert(policyInOrder.queryTextFields.map(_.field) == Vector(title, subtitle))
      assert(policyReversed.queryTextFields.map(_.field) == Vector(title, subtitle))
      assert(policyReversed.queryTextFields.map(_.weight) == Vector(ElasticsearchQueryWeight(3.0), ElasticsearchQueryWeight(1.5)))
      assert(policyReversed.weightOf(title) == Some(ElasticsearchQueryWeight(3.0)))
      assert(policyReversed.weightOf(subtitle) == Some(ElasticsearchQueryWeight(1.5)))
      assert(policyReversed.queryTextFields.map(_.field).zip(Vector(title, subtitle)).forall { case (normalized, canonical) => normalized eq canonical })
    }

    "reject a missing searchable-field weight assignment, reported in document declaration order" in {
      ElasticsearchPolicy(planContractVersion, policy, Vector.empty, ElasticsearchTextOperator.And, None, ElasticsearchTotalHitsPolicy.ExactRequired, defaultSortPolicy) match {
        case Left(errors) =>
          assert(
            errors.toVector ==
              Vector(ElasticsearchQueryPolicyError.MissingQueryWeightAssignment(title.id, 0), ElasticsearchQueryPolicyError.MissingQueryWeightAssignment(subtitle.id, 1))
          )
        case Right(_) => fail("expected missing-assignment errors")
      }
    }

    "reject a duplicate weight assignment, reporting the first and duplicate index" in {
      ElasticsearchPolicy(
        planContractVersion,
        policy,
        Vector(
          ElasticsearchWeightedTextField(title, ElasticsearchQueryWeight(1.0)),
          ElasticsearchWeightedTextField(subtitle, ElasticsearchQueryWeight(1.0)),
          ElasticsearchWeightedTextField(title, ElasticsearchQueryWeight(2.0)),
        ),
        ElasticsearchTextOperator.And,
        None,
        ElasticsearchTotalHitsPolicy.ExactRequired,
        defaultSortPolicy,
      ) match {
        case Left(errors) => assert(errors.toVector.contains(ElasticsearchQueryPolicyError.DuplicateQueryWeightAssignment(title.id, 0, 2)))
        case Right(_)      => fail("expected a duplicate-assignment error")
      }
    }

    "reject a recreated field handle that is not the exact declared instance" in {
      val recreatedTitle = leaderboard.search.gen2.contract.field[BookDocument, String]("title", _.title).text.searchable

      ElasticsearchPolicy(
        planContractVersion,
        policy,
        Vector(ElasticsearchWeightedTextField(recreatedTitle, ElasticsearchQueryWeight(1.0)), ElasticsearchWeightedTextField(subtitle, ElasticsearchQueryWeight(1.0))),
        ElasticsearchTextOperator.And,
        None,
        ElasticsearchTotalHitsPolicy.ExactRequired,
        defaultSortPolicy,
      ) match {
        case Left(errors) => assert(errors.toVector.contains(ElasticsearchQueryPolicyError.UndeclaredQueryTextFieldHandle(recreatedTitle.id, 0)))
        case Right(_)      => fail("expected an undeclared-handle error")
      }
    }

    "reject a non-searchable-or-non-Text field assigned as a weighted query field" in {
      ElasticsearchPolicy(
        planContractVersion,
        policy,
        Vector(
          ElasticsearchWeightedTextField(title, ElasticsearchQueryWeight(1.0)),
          ElasticsearchWeightedTextField(subtitle, ElasticsearchQueryWeight(1.0)),
          ElasticsearchWeightedTextField(genre, ElasticsearchQueryWeight(1.0)),
        ),
        ElasticsearchTextOperator.And,
        None,
        ElasticsearchTotalHitsPolicy.ExactRequired,
        defaultSortPolicy,
      ) match {
        case Left(errors) => assert(errors.toVector.contains(ElasticsearchQueryPolicyError.NonSearchableQueryTextFieldAssignment(genre.id, 2)))
        case Right(_)      => fail("expected genre (non-Text) to be rejected")
      }
    }

    "reject a non-positive query weight" in {
      ElasticsearchPolicy(
        planContractVersion,
        policy,
        Vector(ElasticsearchWeightedTextField(title, ElasticsearchQueryWeight(0.0)), ElasticsearchWeightedTextField(subtitle, ElasticsearchQueryWeight(1.0))),
        ElasticsearchTextOperator.And,
        None,
        ElasticsearchTotalHitsPolicy.ExactRequired,
        defaultSortPolicy,
      ) match {
        case Left(errors) => assert(errors.toVector.contains(ElasticsearchQueryPolicyError.NonPositiveQueryWeight(title.id, 0, 0.0)))
        case Right(_)      => fail("expected a non-positive weight to be rejected")
      }
    }

    "reject NaN and both infinities as query weights instead of admitting non-JSON policy state" in {
      Vector(Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity).foreach { invalid =>
        ElasticsearchPolicy(
          planContractVersion,
          policy,
          Vector(ElasticsearchWeightedTextField(title, ElasticsearchQueryWeight(invalid)), ElasticsearchWeightedTextField(subtitle, ElasticsearchQueryWeight(1.0))),
          ElasticsearchTextOperator.And,
          None,
          ElasticsearchTotalHitsPolicy.ExactRequired,
          defaultSortPolicy,
        ) match {
          case Left(errors) =>
            assert(errors.toVector.exists {
              case ElasticsearchQueryPolicyError.NonPositiveQueryWeight(_, _, value) =>
                java.lang.Double.doubleToLongBits(value) == java.lang.Double.doubleToLongBits(invalid)
              case _ => false
            })
          case Right(_) => fail(s"expected non-finite query weight $invalid to be rejected")
        }
      }
    }

    "reject a non-positive geo scale" in {
      val badGeo = geoScoringPolicy.copy(scale = Distance(BigDecimal(0)))
      ElasticsearchPolicy(planContractVersion, policy, queryTextFields, ElasticsearchTextOperator.And, Some(badGeo), ElasticsearchTotalHitsPolicy.ExactRequired, defaultSortPolicy) match {
        case Left(errors) => assert(errors.toVector.contains(ElasticsearchQueryPolicyError.NonPositiveGeoScale(BigDecimal(0))))
        case Right(_)      => fail("expected a non-positive geo scale to be rejected")
      }
    }

    "reject a negative geo offset" in {
      val badGeo = geoScoringPolicy.copy(offset = Distance(BigDecimal(-1)))
      ElasticsearchPolicy(planContractVersion, policy, queryTextFields, ElasticsearchTextOperator.And, Some(badGeo), ElasticsearchTotalHitsPolicy.ExactRequired, defaultSortPolicy) match {
        case Left(errors) => assert(errors.toVector.contains(ElasticsearchQueryPolicyError.NegativeGeoOffset(BigDecimal(-1))))
        case Right(_)      => fail("expected a negative geo offset to be rejected")
      }
    }

    "reject a geo decay outside the open interval (0, 1)" in {
      Vector(0.0, 1.0, -0.1, 1.1).foreach { badDecay =>
        val badGeo = geoScoringPolicy.copy(decay = ElasticsearchGeoDecay(badDecay))
        ElasticsearchPolicy(planContractVersion, policy, queryTextFields, ElasticsearchTextOperator.And, Some(badGeo), ElasticsearchTotalHitsPolicy.ExactRequired, defaultSortPolicy) match {
          case Left(errors) => assert(errors.toVector.contains(ElasticsearchQueryPolicyError.InvalidGeoDecay(badDecay)), s"decay $badDecay should be rejected")
          case Right(_)      => fail(s"expected geo decay $badDecay to be rejected")
        }
      }
    }

    "reject a non-positive geo weight" in {
      val badGeo = geoScoringPolicy.copy(weight = ElasticsearchQueryWeight(0.0))
      ElasticsearchPolicy(planContractVersion, policy, queryTextFields, ElasticsearchTextOperator.And, Some(badGeo), ElasticsearchTotalHitsPolicy.ExactRequired, defaultSortPolicy) match {
        case Left(errors) => assert(errors.toVector.contains(ElasticsearchQueryPolicyError.NonPositiveGeoWeight(0.0)))
        case Right(_)      => fail("expected a non-positive geo weight to be rejected")
      }
    }

    "reject NaN and both infinities in geo decay and weight" in {
      Vector(Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity).foreach { invalid =>
        val badDecay = geoScoringPolicy.copy(decay = ElasticsearchGeoDecay(invalid))
        ElasticsearchPolicy(planContractVersion, policy, queryTextFields, ElasticsearchTextOperator.And, Some(badDecay), ElasticsearchTotalHitsPolicy.ExactRequired, defaultSortPolicy) match {
          case Left(errors) =>
            assert(errors.toVector.exists {
              case ElasticsearchQueryPolicyError.InvalidGeoDecay(value) =>
                java.lang.Double.doubleToLongBits(value) == java.lang.Double.doubleToLongBits(invalid)
              case _ => false
            })
          case Right(_) => fail(s"expected non-finite geo decay $invalid to be rejected")
        }

        val badWeight = geoScoringPolicy.copy(weight = ElasticsearchQueryWeight(invalid))
        ElasticsearchPolicy(planContractVersion, policy, queryTextFields, ElasticsearchTextOperator.And, Some(badWeight), ElasticsearchTotalHitsPolicy.ExactRequired, defaultSortPolicy) match {
          case Left(errors) =>
            assert(errors.toVector.exists {
              case ElasticsearchQueryPolicyError.NonPositiveGeoWeight(value) =>
                java.lang.Double.doubleToLongBits(value) == java.lang.Double.doubleToLongBits(invalid)
              case _ => false
            })
          case Right(_) => fail(s"expected non-finite geo weight $invalid to be rejected")
        }
      }
    }

    "keep the text operator closed and reject unsupported identity tie-breaker shapes" in {
      assertDoesNotCompile("""ElasticsearchTextOperator("INVALID")""")

      final case class TextIdentityDocument(id: String, body: String)
      val textDeclarations = searchFields[TextIdentityDocument]("text-identity")
      val textIdentity      = textDeclarations.text(_.id).declare
      val textBody          = textDeclarations.text(_.body).searchable.declare
      val textDocument      = textDeclarations.completeDocument(textIdentity)
      val textIndex         = ElasticsearchIndexPolicy.unsafeFrom(textDocument, ElasticsearchPolicyVersion("text-identity-es-v1"), Vector(ElasticsearchTextFieldMapping(textBody, ElasticsearchAnalyzerName.Standard)))
      ElasticsearchPolicy(planContractVersion, textIndex, Vector(ElasticsearchWeightedTextField(textBody, ElasticsearchQueryWeight(1.0))), ElasticsearchTextOperator.And, None, ElasticsearchTotalHitsPolicy.ExactRequired, defaultSortPolicy) match {
        case Left(errors) => assert(errors.toVector.contains(ElasticsearchQueryPolicyError.UnsupportedIdentityTieBreaker(textIdentity.id, SearchFieldKind.Text)))
        case Right(_)      => fail("expected a Text identity to be rejected as a value-sort tie-breaker")
      }

      final case class GeoIdentityDocument(id: GeoPoint, body: String)
      val geoDeclarations = searchFields[GeoIdentityDocument]("geo-identity")
      val geoIdentity      = geoDeclarations.geoPoint(_.id).declare
      val geoBody          = geoDeclarations.text(_.body).searchable.declare
      val geoDocument      = geoDeclarations.completeDocument(geoIdentity)
      val geoIndex         = ElasticsearchIndexPolicy.unsafeFrom(geoDocument, ElasticsearchPolicyVersion("geo-identity-es-v1"), Vector(ElasticsearchTextFieldMapping(geoBody, ElasticsearchAnalyzerName.Standard)))
      ElasticsearchPolicy(planContractVersion, geoIndex, Vector(ElasticsearchWeightedTextField(geoBody, ElasticsearchQueryWeight(1.0))), ElasticsearchTextOperator.And, None, ElasticsearchTotalHitsPolicy.ExactRequired, defaultSortPolicy) match {
        case Left(errors) => assert(errors.toVector.contains(ElasticsearchQueryPolicyError.UnsupportedIdentityTieBreaker(geoIdentity.id, SearchFieldKind.GeoPoint)))
        case Right(_)      => fail("expected a GeoPoint identity to be rejected as a value-sort tie-breaker")
      }
    }

    "produce identical contributions/contractFingerprint for repeated construction of an identical policy" in {
      val first  = policyOrFail()
      val second = policyOrFail()
      assert(first.contributions == second.contributions)
      assert(first.contractFingerprint == second.contractFingerprint)
    }

    "always carry the framework-owned current search-compiler version" in {
      assert(policyOrFail().searchCompilerVersion == ElasticsearchSearchCompilerVersion.Current)
    }

    // Each row below changes exactly one executable query choice; the resulting contribution/fingerprint
    // must differ from the base policy every time, proving every choice genuinely feeds the one fingerprint.
    "change contributions/contractFingerprint independently for every executable query choice" in {
      val base = policyOrFail()

      val changedWeight =
        ElasticsearchPolicy.unsafeFrom(
          planContractVersion,
          policy,
          Vector(ElasticsearchWeightedTextField(title, ElasticsearchQueryWeight(99.0)), ElasticsearchWeightedTextField(subtitle, ElasticsearchQueryWeight(1.5))),
          ElasticsearchTextOperator.Or,
          Some(geoScoringPolicy),
          ElasticsearchTotalHitsPolicy.ExactRequired,
          defaultSortPolicy,
        )
      val changedOperator =
        ElasticsearchPolicy.unsafeFrom(planContractVersion, policy, queryTextFields, ElasticsearchTextOperator.And, Some(geoScoringPolicy), ElasticsearchTotalHitsPolicy.ExactRequired, defaultSortPolicy)
      val changedGeoScale =
        ElasticsearchPolicy.unsafeFrom(
          planContractVersion,
          policy,
          queryTextFields,
          ElasticsearchTextOperator.Or,
          Some(geoScoringPolicy.copy(scale = Distance(BigDecimal(2000)))),
          ElasticsearchTotalHitsPolicy.ExactRequired,
          defaultSortPolicy,
        )
      val changedGeoOffset =
        ElasticsearchPolicy.unsafeFrom(
          planContractVersion,
          policy,
          queryTextFields,
          ElasticsearchTextOperator.Or,
          Some(geoScoringPolicy.copy(offset = Distance(BigDecimal(50)))),
          ElasticsearchTotalHitsPolicy.ExactRequired,
          defaultSortPolicy,
        )
      val changedGeoDecay =
        ElasticsearchPolicy.unsafeFrom(
          planContractVersion,
          policy,
          queryTextFields,
          ElasticsearchTextOperator.Or,
          Some(geoScoringPolicy.copy(decay = ElasticsearchGeoDecay(0.7))),
          ElasticsearchTotalHitsPolicy.ExactRequired,
          defaultSortPolicy,
        )
      val changedGeoWeight =
        ElasticsearchPolicy.unsafeFrom(
          planContractVersion,
          policy,
          queryTextFields,
          ElasticsearchTextOperator.Or,
          Some(geoScoringPolicy.copy(weight = ElasticsearchQueryWeight(9.0))),
          ElasticsearchTotalHitsPolicy.ExactRequired,
          defaultSortPolicy,
        )
      val changedDefaultSort =
        ElasticsearchPolicy.unsafeFrom(
          planContractVersion,
          policy,
          queryTextFields,
          ElasticsearchTextOperator.Or,
          Some(geoScoringPolicy),
          ElasticsearchTotalHitsPolicy.ExactRequired,
          ElasticsearchDefaultSortPolicy(SortDirection.Desc, SortDirection.Desc),
        )

      Vector(
        "text weight"           -> changedWeight,
        "text operator"         -> changedOperator,
        "geo scale"             -> changedGeoScale,
        "geo offset"            -> changedGeoOffset,
        "geo decay"             -> changedGeoDecay,
        "geo weight"            -> changedGeoWeight,
        "default sort policy"   -> changedDefaultSort,
      ).foreach { case (label, changed) =>
        assert(changed.contributions != base.contributions, s"$label should change contributions")
        assert(changed.contractFingerprint != base.contractFingerprint, s"$label should change the contract fingerprint")
      }
    }

    "change contributions/contractFingerprint when geo scoring presence itself changes" in {
      val withGeo    = policyOrFail(geo = Some(geoScoringPolicy))
      val withoutGeo = policyOrFail(geo = None)
      assert(withGeo.contributions != withoutGeo.contributions)
      assert(withGeo.contractFingerprint != withoutGeo.contractFingerprint)
    }

    "reject a wrong-document weighted field, at compile time" in {
      assertDoesNotCompile("""ElasticsearchWeightedTextField[BookDocument](otherHeadline, ElasticsearchQueryWeight(1.0))""")
    }
  }
}
