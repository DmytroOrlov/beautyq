package leaderboard.repo

import cats.data.NonEmptyList
import doobie.Update
import doobie.free.connection as FC
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.fragments
import leaderboard.model.{AttributeValueType, MasterServiceOfferVariant, MasterServiceOfferVariantAttributeDefinition, MasterServiceOfferVariantAttributes, MasterServiceOfferVariantId, QueryFailure}
import leaderboard.model.AttributeValueType.{BigDecimalValue, IntValue}
import leaderboard.model.MasterServiceOfferVariantAttributeDefinition.AnyAttributeDefinition
import leaderboard.model.AttributeMap

private[repo] case class MasterServiceOfferVariantAdditionalAttributes(
  intAttributes: Map[String, Int],
  bigDecimalAttributes: Map[String, BigDecimal],
)

private[repo] object MasterServiceOfferVariantAdditionalAttributes {
  val empty: MasterServiceOfferVariantAdditionalAttributes =
    MasterServiceOfferVariantAdditionalAttributes(Map.empty, Map.empty)
}

private[repo] object MasterServiceOfferVariantAttributesRepository {
  private def attributeValueTypeName(valueType: AttributeValueType): String =
    valueType match {
      case IntValue        => "IntValue"
      case BigDecimalValue => "BigDecimalValue"
    }

  private def unknownAttributeCode(queryName: String, attributeCode: String): QueryFailure =
    QueryFailure.operation(queryName, s"Unknown MasterServiceOfferVariant attribute code: $attributeCode")

  private def attributeStoredInWrongTypeStorage(
    queryName: String,
    attributeCode: String,
    expected: AttributeValueType,
    actual: AttributeValueType,
  ): QueryFailure =
    QueryFailure.operation(
      queryName,
      s"MasterServiceOfferVariant attribute $attributeCode expected storage ${attributeValueTypeName(expected)} but was read from ${attributeValueTypeName(actual)}"
    )

  private def attributeDefinitionByCode(code: String): Option[AnyAttributeDefinition] =
    MasterServiceOfferVariantAttributeDefinition.fromCode(code)

  private def collectStoredAttributes[A](
    queryName: String,
    attributes: Map[String, A],
    actualValueType: AttributeValueType,
    decode: String => Option[MasterServiceOfferVariantAttributeDefinition[A]],
  ): Either[QueryFailure, AttributeMap[A]] =
    attributes.foldLeft[Either[QueryFailure, AttributeMap[A]]](Right(AttributeMap.empty)) {
      case (acc, (attributeCode, value)) =>
        acc.flatMap {
          current =>
            decode(attributeCode) match {
              case Some(definition) =>
                Right(current.updated(definition, value))
              case None =>
                attributeDefinitionByCode(attributeCode) match {
                  case None =>
                    Left(unknownAttributeCode(queryName, attributeCode))
                  case Some(definition) =>
                    Left(attributeStoredInWrongTypeStorage(queryName, attributeCode, definition.valueType, actualValueType))
                }
            }
        }
    }

  def decodeStoredAttributes(
    queryName: String,
    attributes: MasterServiceOfferVariantAdditionalAttributes,
  ): Either[QueryFailure, MasterServiceOfferVariantAttributes] =
    for {
      intAttributes <- collectStoredAttributes(
                         queryName,
                         attributes.intAttributes,
                         IntValue,
                         MasterServiceOfferVariantAttributeDefinition.fromCodeAsInt,
                       )
      bigDecimalAttributes <- collectStoredAttributes(
                                queryName,
                                attributes.bigDecimalAttributes,
                                BigDecimalValue,
                                MasterServiceOfferVariantAttributeDefinition.fromCodeAsBigDecimal,
                              )
    } yield MasterServiceOfferVariantAttributes(
      intAttributes,
      bigDecimalAttributes,
    )

  def encodeStoredAttributes(
    variant: MasterServiceOfferVariant
  ): MasterServiceOfferVariantAdditionalAttributes =
    MasterServiceOfferVariantAdditionalAttributes(
      variant.intAttributes.iterator.map {
        case (attributeDefinition, value) =>
          attributeDefinition.code -> value
      }.toMap,
      variant.bigDecimalAttributes.iterator.map {
        case (attributeDefinition, value) =>
          attributeDefinition.code -> value
      }.toMap,
    )

  private type IntAttributesState = Map[(MasterServiceOfferVariantId, String), Int]
  private type BigDecimalAttributesState = Map[(MasterServiceOfferVariantId, String), BigDecimal]

  case class DummyState(
    intAttributes: IntAttributesState,
    bigDecimalAttributes: BigDecimalAttributesState,
  )

  object DummyState {
    val empty: DummyState = DummyState(Map.empty, Map.empty)
  }

  class Dummy {
    private def loadAttributes[A](
      state: Map[(MasterServiceOfferVariantId, String), A],
      variantId: MasterServiceOfferVariantId,
    ): Map[String, A] =
      state.iterator.collect {
        case ((id, attributeCode), value) if id == variantId =>
          attributeCode -> value
      }.toMap

    private def replaceAttributes[A](
      state: Map[(MasterServiceOfferVariantId, String), A],
      variantId: MasterServiceOfferVariantId,
      attributes: Map[String, A],
    ): Map[(MasterServiceOfferVariantId, String), A] =
      state.filterNot {
        case ((storedVariantId, _), _) =>
          storedVariantId == variantId
      } ++ attributes.iterator.map {
        case (attributeCode, value) =>
          (variantId -> attributeCode) -> value
      }

    def load(
      state: DummyState,
      variantId: MasterServiceOfferVariantId,
    ): MasterServiceOfferVariantAdditionalAttributes =
      MasterServiceOfferVariantAdditionalAttributes(
        loadAttributes(state.intAttributes, variantId),
        loadAttributes(state.bigDecimalAttributes, variantId),
      )

    def replace(
      variantId: MasterServiceOfferVariantId,
      attributes: MasterServiceOfferVariantAdditionalAttributes,
    )(state: DummyState): DummyState = {
      state.copy(
        intAttributes = replaceAttributes(state.intAttributes, variantId, attributes.intAttributes),
        bigDecimalAttributes = replaceAttributes(state.bigDecimalAttributes, variantId, attributes.bigDecimalAttributes),
      )
    }
  }

  class Postgres {
    private type IntAttributeRow = (String, Int)
    private type BigDecimalAttributeRow = (String, BigDecimal)
    private type IntAttributeStoredRow = (MasterServiceOfferVariantId, String, Int)
    private type BigDecimalAttributeStoredRow = (MasterServiceOfferVariantId, String, BigDecimal)
    private type IntAttributeInsertRow = (MasterServiceOfferVariantId, String, Int)
    private type BigDecimalAttributeInsertRow = (MasterServiceOfferVariantId, String, BigDecimal)

    private def loadRowsForVariant[A](
      rows: List[(String, A)]
    ): Map[String, A] =
      rows.toMap

    private def loadManyRows[A](
      rows: List[(MasterServiceOfferVariantId, String, A)]
    ): Map[MasterServiceOfferVariantId, Map[String, A]] =
      rows.groupMap(_._1) {
        case (_, attributeCode, value) =>
          attributeCode -> value
      }.view.mapValues(_.toMap).toMap

    private def mergeLoadedAttributes(
      variantIds: List[MasterServiceOfferVariantId],
      intRows: List[IntAttributeStoredRow],
      bigDecimalRows: List[BigDecimalAttributeStoredRow],
    ): Map[MasterServiceOfferVariantId, MasterServiceOfferVariantAdditionalAttributes] = {
      val intAttributesById        = loadManyRows(intRows)
      val bigDecimalAttributesById = loadManyRows(bigDecimalRows)

      variantIds.iterator.map {
        variantId =>
          variantId -> MasterServiceOfferVariantAdditionalAttributes(
            intAttributesById.getOrElse(variantId, Map.empty),
            bigDecimalAttributesById.getOrElse(variantId, Map.empty),
          )
      }.toMap
    }

    private def selectIntAttributes(variantIds: NonEmptyList[MasterServiceOfferVariantId]): ConnectionIO[List[IntAttributeStoredRow]] =
      (fr"""select master_service_offer_variant_id, attribute_code, value
           |from master_service_offer_variant_int_attributes
           |where""".stripMargin ++
        fragments.in(fr"master_service_offer_variant_id", variantIds) ++
        fr"order by master_service_offer_variant_id asc, attribute_code asc")
        .query[IntAttributeStoredRow]
        .to[List]

    private def selectBigDecimalAttributes(
      variantIds: NonEmptyList[MasterServiceOfferVariantId]
    ): ConnectionIO[List[BigDecimalAttributeStoredRow]] =
      (fr"""select master_service_offer_variant_id, attribute_code, value
           |from master_service_offer_variant_bigdecimal_attributes
           |where""".stripMargin ++
        fragments.in(fr"master_service_offer_variant_id", variantIds) ++
        fr"order by master_service_offer_variant_id asc, attribute_code asc")
        .query[BigDecimalAttributeStoredRow]
        .to[List]

    def createTables: ConnectionIO[Unit] =
      for {
        _ <- sql"""create table if not exists master_service_offer_variant_int_attributes (
                  |  master_service_offer_variant_id uuid not null,
                  |  attribute_code text not null,
                  |  value int not null,
                  |  primary key (master_service_offer_variant_id, attribute_code),
                  |  constraint master_service_offer_variant_int_attributes_variant_fk
                  |    foreign key (master_service_offer_variant_id) references master_service_offer_variants(id)
                  |) without oids
                  |""".stripMargin.update.run
        _ <- sql"""create table if not exists master_service_offer_variant_bigdecimal_attributes (
                  |  master_service_offer_variant_id uuid not null,
                  |  attribute_code text not null,
                  |  value numeric not null,
                  |  primary key (master_service_offer_variant_id, attribute_code),
                  |  constraint master_service_offer_variant_bigdecimal_attributes_variant_fk
                  |    foreign key (master_service_offer_variant_id) references master_service_offer_variants(id)
                  |) without oids
                  |""".stripMargin.update.run
      } yield ()

    def load(variantId: MasterServiceOfferVariantId): ConnectionIO[MasterServiceOfferVariantAdditionalAttributes] =
      for {
        intAttributes <- sql"""select attribute_code, value
                               |from master_service_offer_variant_int_attributes
                               |where master_service_offer_variant_id = $variantId
                               |order by attribute_code asc
                               |""".stripMargin.query[IntAttributeRow].to[List]
        bigDecimalAttributes <- sql"""select attribute_code, value
                                      |from master_service_offer_variant_bigdecimal_attributes
                                      |where master_service_offer_variant_id = $variantId
                                      |order by attribute_code asc
                                      |""".stripMargin.query[BigDecimalAttributeRow].to[List]
      } yield MasterServiceOfferVariantAdditionalAttributes(
        loadRowsForVariant(intAttributes),
        loadRowsForVariant(bigDecimalAttributes),
      )

    def loadMany(
      variantIds: List[MasterServiceOfferVariantId]
    ): ConnectionIO[Map[MasterServiceOfferVariantId, MasterServiceOfferVariantAdditionalAttributes]] =
      NonEmptyList.fromList(variantIds) match {
        case Some(ids) =>
          for {
            intRows        <- selectIntAttributes(ids)
            bigDecimalRows <- selectBigDecimalAttributes(ids)
          } yield mergeLoadedAttributes(variantIds, intRows, bigDecimalRows)
        case None =>
          FC.pure(Map.empty)
      }

    def replace(
      variantId: MasterServiceOfferVariantId,
      attributes: MasterServiceOfferVariantAdditionalAttributes,
    ): ConnectionIO[Unit] =
      for {
        _ <- sql"""delete from master_service_offer_variant_int_attributes
                   |where master_service_offer_variant_id = $variantId
                   |""".stripMargin.update.run
        _ <- sql"""delete from master_service_offer_variant_bigdecimal_attributes
                   |where master_service_offer_variant_id = $variantId
                   |""".stripMargin.update.run
        _ <- insertIntAttributes(
               attributes.intAttributes.toList.map {
                 case (attributeCode, value) =>
                   (variantId, attributeCode, value)
               }
             )
        _ <- insertBigDecimalAttributes(
               attributes.bigDecimalAttributes.toList.map {
                 case (attributeCode, value) =>
                   (variantId, attributeCode, value)
               }
             )
      } yield ()

    private def insertIntAttributes(rows: List[IntAttributeInsertRow]): ConnectionIO[Int] =
      if (rows.isEmpty) {
        FC.pure(0)
      } else {
        Update[IntAttributeInsertRow](
          """insert into master_service_offer_variant_int_attributes (
            |  master_service_offer_variant_id,
            |  attribute_code,
            |  value
            |)
            |values (?, ?, ?)
            |""".stripMargin
        ).updateMany(rows)
      }

    private def insertBigDecimalAttributes(rows: List[BigDecimalAttributeInsertRow]): ConnectionIO[Int] =
      if (rows.isEmpty) {
        FC.pure(0)
      } else {
        Update[BigDecimalAttributeInsertRow](
          """insert into master_service_offer_variant_bigdecimal_attributes (
            |  master_service_offer_variant_id,
            |  attribute_code,
            |  value
            |)
            |values (?, ?, ?)
            |""".stripMargin
        ).updateMany(rows)
      }
  }
}
