package leaderboard.search.beautyq.gen2.materialization

import doobie.free.connection.ConnectionIO
import doobie.free.{connection => FC}
import doobie.implicits.*
import izumi.functional.bio.{Error2, F}
import leaderboard.model.*
import leaderboard.repo.*
import leaderboard.repo.given
import leaderboard.search.gen2.contract.SearchValueCodec
import leaderboard.search.gen2.core.materialization.{SearchSnapshotSource, VersionedSnapshot}
import leaderboard.sql.SQL

import java.time.Clock

/** Neutral hook executed inside the same snapshot transaction, after schema rows and before
  * master/variant-side rows: production callers never provide one (the no-op default keeps the
  * ordinary two-argument [[BeautyQSearchSnapshotSource.Postgres]] constructor unchanged); the
  * deterministic concurrent-mutation test uses it to pause the read transaction mid-flight.
  */
private[leaderboard] trait SnapshotReadHook {
  def afterServicesAndSchemas: ConnectionIO[Unit]
}

private[leaderboard] object SnapshotReadHook {
  val NoOp: SnapshotReadHook =
    new SnapshotReadHook {
      def afterServicesAndSchemas: ConnectionIO[Unit] = FC.pure(())
    }
}

object BeautyQSearchSnapshotSource {
  private val LoadQueryName = "load-beautyq-gen2-search-snapshot"

  private type SchemaItemRow = (ServiceId, String, Boolean)
  private type VariantBaseRow = (MasterServiceOfferVariantId, MasterServiceOfferId, MasterLocationId, BigDecimal, BigDecimal, Int)
  private type VariantNumericAttributeRow = (MasterServiceOfferVariantId, String, BigDecimal)

  /** Reads every BeautyQ source table through one repeatable-read, read-only transaction
    * ([[leaderboard.sql.SQL.readOnlyRepeatableRead]]) rather than the per-call transactions
    * [[leaderboard.repo.Categories]]/[[leaderboard.repo.Services]]/... each open - existing repository
    * interfaces are intentionally never injected here, since they cannot share one transaction.
    */
  final class Postgres[F[+_, +_]: Error2](
    sql: SQL[F],
    clock: Clock,
    hook: SnapshotReadHook = SnapshotReadHook.NoOp,
  ) extends SearchSnapshotSource[F, SnapshotLoadError, BeautyQSearchSnapshot] {

    def load: F[SnapshotLoadError, VersionedSnapshot[BeautyQSearchSnapshot]] =
      sql
        .readOnlyRepeatableRead(LoadQueryName)(combinedRead)
        .leftMap(SnapshotLoadError.Repository.apply)
        .flatMap {
          case Right(snapshot) => F.pure(snapshot)
          case Left(loadError) => F.fail(loadError)
        }
        .map {
          snapshot =>
            VersionedSnapshot(
              value = snapshot,
              contentFingerprint = BeautyQSnapshotFingerprint.compute(snapshot),
              sourceRevision = None,
              capturedAt = clock.instant(),
            )
        }

    private def combinedRead: ConnectionIO[Either[SnapshotLoadError, BeautyQSearchSnapshot]] =
      for {
        categories <- selectCategories
        services <- selectServices
        schemaItemRows <- selectSchemaItems
        _ <- hook.afterServicesAndSchemas
        masters <- selectMasters
        masterLocations <- selectMasterLocations
        masterServiceOffers <- selectMasterServiceOffers
        variantBaseRows <- selectVariantBaseRows
        variantNumericAttributeRows <- selectVariantNumericAttributeRows
      } yield for {
        schemas  <- buildSchemas(services, schemaItemRows)
        variants <- buildVariants(variantBaseRows, variantNumericAttributeRows)
      } yield BeautyQSearchSnapshot(
        categories = categories.toVector,
        services = services.toVector,
        serviceVariantSchemas = schemas,
        masters = masters.toVector,
        masterLocations = masterLocations.toVector,
        masterServiceOffers = masterServiceOffers.toVector,
        masterServiceOfferVariants = variants,
      )

    private def selectCategories: ConnectionIO[List[Category]] =
      sql"""select id, code, parent_id, depth, name
           |from category
           |order by id asc
           |""".stripMargin.query[Category].to[List]

    private def selectServices: ConnectionIO[List[Service]] =
      sql"""select id, code, category_id, name
           |from service
           |order by id asc
           |""".stripMargin.query[Service].to[List]

    private def selectSchemaItems: ConnectionIO[List[SchemaItemRow]] =
      sql"""select service_id, attribute_code, required
           |from service_variant_schema_item
           |order by service_id asc, attribute_code asc
           |""".stripMargin.query[SchemaItemRow].to[List]

    private def selectMasters: ConnectionIO[List[Master]] =
      sql"""select id, name
           |from master
           |order by id asc
           |""".stripMargin.query[Master].to[List]

    private def selectMasterLocations: ConnectionIO[List[MasterLocation]] =
      sql"""select id, master_id, name, address, lat, lon
           |from master_location
           |order by id asc
           |""".stripMargin.query[MasterLocation].to[List]

    private def selectMasterServiceOffers: ConnectionIO[List[MasterServiceOffer]] =
      sql"""select id, master_id, service_id
           |from master_service_offer
           |order by id asc
           |""".stripMargin.query[MasterServiceOffer].to[List]

    private def selectVariantBaseRows: ConnectionIO[List[VariantBaseRow]] =
      sql"""select id, master_service_offer_id, master_location_id, price_from, price_to, duration_min
           |from master_service_offer_variant
           |order by id asc
           |""".stripMargin.query[VariantBaseRow].to[List]

    private def selectVariantNumericAttributeRows: ConnectionIO[List[VariantNumericAttributeRow]] =
      sql"""select master_service_offer_variant_id, attribute_code, value
           |from master_service_offer_variant_numeric_attributes
           |order by master_service_offer_variant_id asc, attribute_code asc
           |""".stripMargin.query[VariantNumericAttributeRow].to[List]

    private def buildSchemas(
      services: List[Service],
      schemaItemRows: List[SchemaItemRow],
    ): Either[SnapshotLoadError, Vector[ServiceVariantSchema]] = {
      val serviceIds = services.iterator.map(_.id).toSet

      schemaItemRows
        .sortBy { case (serviceId, attributeCode, required) => (ServiceId.unwrap(serviceId).toString, attributeCode, required.toString) }
        .collectFirst {
          case (serviceId, _, _) if !serviceIds.contains(serviceId) =>
            SnapshotLoadError.InvalidStoredData(
              QueryFailure.operation(
                LoadQueryName,
                s"ServiceVariantSchema row references missing Service ${ServiceId.unwrap(serviceId)}",
              )
            )
        } match {
        case Some(error) => Left(error)
        case None        =>
          val rowsByServiceId: Map[ServiceId, List[(String, Boolean)]] =
            schemaItemRows.groupMap { case (serviceId, attributeCode, required) => serviceId } {
              case (_, attributeCode, required) => (attributeCode, required)
            }

          services.foldRight[Either[SnapshotLoadError, List[ServiceVariantSchema]]](Right(Nil)) {
            case (service, acc) =>
              for {
                tail <- acc
                schema <- ServiceVariantSchemas
                  .decodeStoredSchema(LoadQueryName, service.id, rowsByServiceId.getOrElse(service.id, Nil))
                  .left
                  .map(SnapshotLoadError.InvalidStoredData.apply)
              } yield schema :: tail
          }.map(_.toVector)
      }
    }

    private def buildVariants(
      variantBaseRows: List[VariantBaseRow],
      variantNumericAttributeRows: List[VariantNumericAttributeRow],
    ): Either[SnapshotLoadError, Vector[MasterServiceOfferVariant]] = {
      val variantIds = variantBaseRows.iterator.map(_._1).toSet

      variantNumericAttributeRows
        .sortBy { case (variantId, attributeCode, value) => (MasterServiceOfferVariantId.unwrap(variantId).toString, attributeCode, canonicalDecimal(value)) }
        .collectFirst {
          case (variantId, _, _) if !variantIds.contains(variantId) =>
            SnapshotLoadError.InvalidStoredData(
              QueryFailure.operation(
                LoadQueryName,
                s"MasterServiceOfferVariant attribute row references missing Variant ${MasterServiceOfferVariantId.unwrap(variantId)}",
              )
            )
        } match {
        case Some(error) => Left(error)
        case None        =>
          val numericRowsByVariantId: Map[MasterServiceOfferVariantId, Map[String, BigDecimal]] =
            variantNumericAttributeRows
              .groupMap(_._1) { case (_, attributeCode, value) => attributeCode -> value }
              .view.mapValues(_.toMap).toMap

          variantBaseRows
            .foldRight[Either[SnapshotLoadError, List[MasterServiceOfferVariant]]](Right(Nil)) {
              case ((id, masterServiceOfferId, masterLocationId, priceFrom, priceTo, durationMin), acc) =>
                acc.flatMap {
                  tail =>
                    val rawNumericAttributes = numericRowsByVariantId.getOrElse(id, Map.empty[String, BigDecimal])

                    val builtVariant =
                      for {
                        additionalAttributes <- MasterServiceOfferVariantAttributesRepository
                          .decodeStoredNumericAttributes(LoadQueryName, rawNumericAttributes)
                          .left.map(SnapshotLoadError.InvalidStoredData.apply)
                        attributes <- MasterServiceOfferVariantAttributesRepository
                          .decodeStoredAttributes(LoadQueryName, additionalAttributes)
                          .left.map(SnapshotLoadError.InvalidStoredData.apply)
                        variant <- MasterServiceOfferVariant
                          .make(id, masterServiceOfferId, masterLocationId, priceFrom, priceTo, durationMin, attributes)
                          .left.map(error => SnapshotLoadError.InvalidStoredData(QueryFailure.operation(LoadQueryName, error.message)))
                      } yield variant

                    builtVariant.map(_ :: tail)
                }
            }.map(_.toVector)
      }
    }

    private def canonicalDecimal(value: BigDecimal): String =
      SearchValueCodec.bigDecimal.encodeCanonical(value)
  }
}
