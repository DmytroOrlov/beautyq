package leaderboard.repo

import org.scalatest.wordspec.AnyWordSpec

final case class TestOwnerId(value: String)
final case class TestAggregateRow(ownerId: TestOwnerId, code: String, required: Boolean)
final case class TestAggregate(ownerId: TestOwnerId, items: List[TestAggregateRow])

final class RepoValueSourceDerivationSpec extends AnyWordSpec {

  "RepoValueSource.derived" should {
    "derive the aggregate value model name from the aggregate case class label" in {
      val source = RepoValueSource.derived[TestAggregate, TestOwnerId, TestAggregateRow](_.ownerId)

      assert(source.valueModelName == "TestAggregate")
    }

    "derive the physical row source model/source name from the row case class" in {
      val source = RepoValueSource.derived[TestAggregate, TestOwnerId, TestAggregateRow](_.ownerId)

      assert(source.rowSource.modelName == "TestAggregateRow")
      assert(source.rowSource.sourceName == "test_aggregate_row")
    }

    "derive the key field label and column from the aggregate selector, without any raw string" in {
      val source = RepoValueSource.derived[TestAggregate, TestOwnerId, TestAggregateRow](_.ownerId)

      assert(source.keyField.label == "ownerId")
      assert(source.keyField.column == "owner_id")
    }
  }
}
