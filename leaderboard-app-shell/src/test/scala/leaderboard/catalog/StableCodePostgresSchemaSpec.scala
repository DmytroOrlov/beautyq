package leaderboard

import doobie.implicits.*
import leaderboard.sql.SQL
import zio.IO

final class StableCodePostgresSchemaSpec extends ProdTest {

  // Categories[IO] and Services[IO] are already LeaderboardTest memoization roots, so their DDL has
  // already run for any test in this class; these two schema checks only need SQL[IO] directly.
  "the stable-code Postgres schema" should {
    "expose category.code and service.code as present, not-null columns" in {
      (db: SQL[IO]) =>
        for {
          categoryCodeNullable <- db.execute("stable-code-category-column-check") {
            sql"""
              select is_nullable
              from information_schema.columns
              where table_schema = 'public'
                and table_name = 'category'
                and column_name = 'code'
            """.query[String].option
          }
          serviceCodeNullable <- db.execute("stable-code-service-column-check") {
            sql"""
              select is_nullable
              from information_schema.columns
              where table_schema = 'public'
                and table_name = 'service'
                and column_name = 'code'
            """.query[String].option
          }
          _ <- assertIO(categoryCodeNullable.contains("NO"))
          _ <- assertIO(serviceCodeNullable.contains("NO"))
        } yield ()
    }

    "expose exactly one current-schema unique index for each code column" in {
      (db: SQL[IO]) =>
        for {
          categoryIndexNames <- db.execute("stable-code-category-index-check") {
            sql"""
              select indexname
              from pg_indexes
              where schemaname = 'public'
                and tablename = 'category'
                and indexname = 'category_code_uidx'
            """.query[String].to[List]
          }
          serviceIndexNames <- db.execute("stable-code-service-index-check") {
            sql"""
              select indexname
              from pg_indexes
              where schemaname = 'public'
                and tablename = 'service'
                and indexname = 'service_code_uidx'
            """.query[String].to[List]
          }
          _ <- assertIO(categoryIndexNames == List("category_code_uidx"))
          _ <- assertIO(serviceIndexNames == List("service_code_uidx"))
        } yield ()
    }
  }
}
