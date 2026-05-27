package leaderboard.repo

import cats.data.NonEmptyList
import doobie.Update
import doobie.free.connection as FC
import doobie.free.connection.ConnectionIO
import doobie.implicits.*
import doobie.postgres.implicits.*
import doobie.util.fragments
import leaderboard.model.{AttributeDefinition, AttributeMap, BigDecimalAttributeDefinition, BooleanAttributeDefinition, CodedEnumValue, EnumAttributeDefinition, IntAttributeDefinition, MasterServiceOfferVariant, MasterServiceOfferVariantAttributes, MasterServiceOfferVariantId, QueryFailure}
import leaderboard.model.AttributeDefinition.AnyAttributeDefinition

private[repo] case class MasterServiceOfferVariantAdditionalAttributes(
  intAttributes: Map[String, Int],
  bigDecimalAttributes: Map[String, BigDecimal],
  enumAttributes: Map[String, CodedEnumValue],
  booleanAttributes: Map[String, Boolean],
)

private[repo] object MasterServiceOfferVariantAdditionalAttributes {
  val empty: MasterServiceOfferVariantAdditionalAttributes =
    MasterServiceOfferVariantAdditionalAttributes(Map.empty, Map.empty, Map.empty, Map.empty)
}

private[repo] object MasterServiceOfferVariantAttributesRepository {
  private def unknownAttributeCode(queryName: String, attributeCode: String): QueryFailure =
    QueryFailure.operation(queryName, s"Unknown MasterServiceOfferVariant attribute code: $attributeCode")

  private def unsupportedNumericAttributeDefinition(
    queryName: String,
    attributeCode: String,
    actual: AnyAttributeDefinition,
  ): QueryFailure =
    QueryFailure.operation(
      queryName,
      s"Unsupported numeric attribute definition for MasterServiceOfferVariant attribute $attributeCode: ${actual.valueType}",
    )

  private def intNumericValueOutsideRange(
    queryName: String,
    attributeCode: String,
    value: BigDecimal,
  ): QueryFailure =
    QueryFailure.operation(
      queryName,
      s"MasterServiceOfferVariant attribute $attributeCode expected Int-compatible numeric value but was outside Int range: $value",
    )

  private def nonIntegerNumericValueForInt(
    queryName: String,
    attributeCode: String,
    value: BigDecimal,
  ): QueryFailure =
    QueryFailure.operation(
      queryName,
      s"MasterServiceOfferVariant attribute $attributeCode expected Int-compatible numeric value but got non-integer numeric value: $value",
    )

  private def decodeBooleanValue(
    queryName: String,
    attributeCode: String,
    value: BigDecimal,
  ): Either[QueryFailure, Boolean] =
    value.toBigIntExact match {
      case Some(bigInt) if bigInt == BigInt(0) =>
        Right(false)
      case Some(bigInt) if bigInt == BigInt(1) =>
        Right(true)
      case Some(_) =>
        Left(QueryFailure.operation(queryName, s"MasterServiceOfferVariant attribute $attributeCode expected Boolean-compatible numeric value 0 or 1 but got $value"))
      case None =>
        Left(
          QueryFailure.operation(
            queryName,
            s"MasterServiceOfferVariant attribute $attributeCode expected Boolean-compatible numeric value 0 or 1 but got non-integer numeric value: $value",
          )
        )
    }

  private def unknownEnumIntCode(
    queryName: String,
    attributeCode: String,
    value: Int,
  ): QueryFailure =
    QueryFailure.operation(
      queryName,
      s"MasterServiceOfferVariant attribute $attributeCode has unknown enum int code: $value",
    )

  private def enumValueNotAllowedForAttribute(
    queryName: String,
    attributeCode: String,
    value: CodedEnumValue,
  ): QueryFailure =
    QueryFailure.operation(
      queryName,
      s"MasterServiceOfferVariant enum attribute $attributeCode does not accept enum value ${value.stringCode}",
    )

  private def attributeDefinitionByCode(code: String): Option[AnyAttributeDefinition] =
    AttributeDefinition.fromCode(code)

  private def collectStoredAttributes[A](
    queryName: String,
    attributes: Map[String, A],
    decode: String => Option[AttributeDefinition[A]],
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
                    Left(unsupportedNumericAttributeDefinition(queryName, attributeCode, definition))
                }
            }
        }
    }

  private def decodeIntValue(
    queryName: String,
    attributeCode: String,
    value: BigDecimal,
  ): Either[QueryFailure, Int] =
    value.toBigIntExact match {
      case Some(bigInt) if bigInt.isValidInt =>
        Right(bigInt.toInt)
      case Some(_) =>
        Left(intNumericValueOutsideRange(queryName, attributeCode, value))
      case None =>
        Left(nonIntegerNumericValueForInt(queryName, attributeCode, value))
    }

  def decodeStoredNumericAttributes(
    queryName: String,
    attributes: Map[String, BigDecimal],
  ): Either[QueryFailure, MasterServiceOfferVariantAdditionalAttributes] =
    attributes.foldLeft[Either[QueryFailure, MasterServiceOfferVariantAdditionalAttributes]](
      Right(MasterServiceOfferVariantAdditionalAttributes.empty)
    ) {
      case (acc, (attributeCode, value)) =>
        acc.flatMap {
          current =>
            AttributeDefinition.fromCode(attributeCode) match {
              case None =>
                Left(unknownAttributeCode(queryName, attributeCode))
              case Some(_: IntAttributeDefinition) =>
                decodeIntValue(queryName, attributeCode, value).map {
                  decoded =>
                    current.copy(intAttributes = current.intAttributes.updated(attributeCode, decoded))
                }
              case Some(_: BigDecimalAttributeDefinition) =>
                Right(current.copy(bigDecimalAttributes = current.bigDecimalAttributes.updated(attributeCode, value)))
              case Some(_: BooleanAttributeDefinition) =>
                decodeBooleanValue(queryName, attributeCode, value).map {
                  decoded =>
                    current.copy(booleanAttributes = current.booleanAttributes.updated(attributeCode, decoded))
                }
              case Some(definition: EnumAttributeDefinition[?]) =>
                decodeIntValue(queryName, attributeCode, value).flatMap {
                  intCode =>
                    definition.fromIntCode(intCode) match {
                      case Some(enumValue) =>
                        Right(current.copy(enumAttributes = current.enumAttributes.updated(attributeCode, enumValue)))
                      case None =>
                        Left(unknownEnumIntCode(queryName, attributeCode, intCode))
                    }
                }
              case Some(definition) =>
                Left(unsupportedNumericAttributeDefinition(queryName, attributeCode, definition))
            }
        }
    }

  private def collectStoredEnumAttributes(
    queryName: String,
    attributes: Map[String, CodedEnumValue],
  ): Either[QueryFailure, AttributeMap[CodedEnumValue]] =
    attributes.foldLeft[Either[QueryFailure, AttributeMap[CodedEnumValue]]](Right(AttributeMap.empty)) {
      case (acc, (attributeCode, value)) =>
        acc.flatMap {
          current =>
            AttributeDefinition.fromCodeAsEnum(attributeCode) match {
              case Some(definition) =>
                Right(current.updated(definition.asInstanceOf[AttributeDefinition[CodedEnumValue]], value))
              case None =>
                attributeDefinitionByCode(attributeCode) match {
                  case None =>
                    Left(unknownAttributeCode(queryName, attributeCode))
                  case Some(definition) =>
                    Left(unsupportedNumericAttributeDefinition(queryName, attributeCode, definition))
                }
            }
        }
    }

  def decodeStoredAttributes(
    queryName: String,
    attributes: MasterServiceOfferVariantAdditionalAttributes,
  ): Either[QueryFailure, MasterServiceOfferVariantAttributes] =
    for {
      intAttributes <- collectStoredAttributes[Int](
        queryName,
        attributes.intAttributes,
        AttributeDefinition.fromCodeAsInt,
      )
      bigDecimalAttributes <- collectStoredAttributes[BigDecimal](
        queryName,
        attributes.bigDecimalAttributes,
        AttributeDefinition.fromCodeAsBigDecimal,
      )
      booleanAttributes <- collectStoredAttributes[Boolean](
        queryName,
        attributes.booleanAttributes,
        AttributeDefinition.fromCodeAsBoolean,
      )
      enumAttributes <- collectStoredEnumAttributes(
        queryName,
        attributes.enumAttributes,
      )
    } yield MasterServiceOfferVariantAttributes(
      intAttributes,
      bigDecimalAttributes,
      enumAttributes,
      booleanAttributes,
    )

  private def validateEnumAttributeValue(
    queryName: String,
    attributeDefinition: EnumAttributeDefinition[?],
    value: CodedEnumValue,
  ): Either[QueryFailure, Unit] =
    attributeDefinition.fromIntCode(value.intCode) match {
      case Some(decoded) if decoded == value =>
        Right(())
      case _ =>
        Left(enumValueNotAllowedForAttribute(queryName, attributeDefinition.code, value))
    }

  private def collectEncodedEnumAttributes(
    queryName: String,
    variant: MasterServiceOfferVariant,
  ): Either[QueryFailure, Map[String, CodedEnumValue]] =
    variant.enumAttributes.iterator.foldLeft[Either[QueryFailure, Map[String, CodedEnumValue]]](Right(Map.empty)) {
      case (acc, (attributeDefinition, value)) =>
        acc.flatMap {
          current =>
            attributeDefinition match {
              case enumDefinition: EnumAttributeDefinition[?] =>
                validateEnumAttributeValue(queryName, enumDefinition, value).map {
                  _ =>
                    current.updated(enumDefinition.code, value)
                }
              case other =>
                Left(unsupportedNumericAttributeDefinition(queryName, other.code, other))
            }
        }
    }

  private def collectEncodedBooleanAttributes(
    queryName: String,
    variant: MasterServiceOfferVariant,
  ): Either[QueryFailure, Map[String, Boolean]] =
    variant.booleanAttributes.iterator.foldLeft[Either[QueryFailure, Map[String, Boolean]]](Right(Map.empty)) {
      case (acc, (attributeDefinition, value)) =>
        acc.flatMap {
          current =>
            attributeDefinition match {
              case booleanDefinition: BooleanAttributeDefinition =>
                Right(current.updated(booleanDefinition.code, value))
              case other =>
                Left(unsupportedNumericAttributeDefinition(queryName, other.code, other))
            }
        }
    }

  def encodeStoredAttributes(
    queryName: String,
    variant: MasterServiceOfferVariant,
  ): Either[QueryFailure, MasterServiceOfferVariantAdditionalAttributes] =
    for {
      enumAttributes    <- collectEncodedEnumAttributes(queryName, variant)
      booleanAttributes <- collectEncodedBooleanAttributes(queryName, variant)
    } yield {
      MasterServiceOfferVariantAdditionalAttributes(
        variant.intAttributes.iterator.map {
          case (attributeDefinition, value) =>
            attributeDefinition.code -> value
        }.toMap,
        variant.bigDecimalAttributes.iterator.map {
          case (attributeDefinition, value) =>
            attributeDefinition.code -> value
        }.toMap,
        enumAttributes,
        booleanAttributes,
      )
    }

  private type IntAttributesState        = Map[(MasterServiceOfferVariantId, String), Int]
  private type BigDecimalAttributesState = Map[(MasterServiceOfferVariantId, String), BigDecimal]
  private type EnumAttributesState       = Map[(MasterServiceOfferVariantId, String), CodedEnumValue]
  private type BooleanAttributesState    = Map[(MasterServiceOfferVariantId, String), Boolean]

  case class DummyState(
    intAttributes: IntAttributesState,
    bigDecimalAttributes: BigDecimalAttributesState,
    enumAttributes: EnumAttributesState,
    booleanAttributes: BooleanAttributesState,
  )

  object DummyState {
    val empty: DummyState = DummyState(Map.empty, Map.empty, Map.empty, Map.empty)
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
        loadAttributes(state.enumAttributes, variantId),
        loadAttributes(state.booleanAttributes, variantId),
      )

    def replace(
      variantId: MasterServiceOfferVariantId,
      attributes: MasterServiceOfferVariantAdditionalAttributes,
    )(state: DummyState
    ): DummyState = {
      state.copy(
        intAttributes        = replaceAttributes(state.intAttributes, variantId, attributes.intAttributes),
        bigDecimalAttributes = replaceAttributes(state.bigDecimalAttributes, variantId, attributes.bigDecimalAttributes),
        enumAttributes       = replaceAttributes(state.enumAttributes, variantId, attributes.enumAttributes),
        booleanAttributes    = replaceAttributes(state.booleanAttributes, variantId, attributes.booleanAttributes),
      )
    }
  }

  class Postgres {
    private type NumericAttributeRow       = (String, BigDecimal)
    private type NumericAttributeStoredRow = (MasterServiceOfferVariantId, String, BigDecimal)
    private type NumericAttributeInsertRow = (MasterServiceOfferVariantId, String, BigDecimal)

    private def encodeBooleanValue(value: Boolean): BigDecimal =
      if (value) BigDecimal(1) else BigDecimal(0)

    private def loadRowsForVariant(
      rows: List[NumericAttributeRow]
    ): Map[String, BigDecimal] =
      rows.toMap

    private def loadManyRows(
      rows: List[NumericAttributeStoredRow]
    ): Map[MasterServiceOfferVariantId, Map[String, BigDecimal]] =
      rows
        .groupMap(_._1) {
          case (_, attributeCode, value) =>
            attributeCode -> value
        }.view.mapValues(_.toMap).toMap

    private def mergeLoadedAttributes(
      queryName: String,
      variantIds: List[MasterServiceOfferVariantId],
      rows: List[NumericAttributeStoredRow],
    ): Either[QueryFailure, Map[MasterServiceOfferVariantId, MasterServiceOfferVariantAdditionalAttributes]] = {
      val rowsByVariantId = loadManyRows(rows)

      variantIds.foldLeft[Either[QueryFailure, Map[MasterServiceOfferVariantId, MasterServiceOfferVariantAdditionalAttributes]]](
        Right(Map.empty)
      ) {
        case (acc, variantId) =>
          acc.flatMap {
            current =>
              decodeStoredNumericAttributes(queryName, rowsByVariantId.getOrElse(variantId, Map.empty)).map {
                decoded =>
                  current.updated(variantId, decoded)
              }
          }
      }
    }

    private def selectNumericAttributes(
      variantIds: NonEmptyList[MasterServiceOfferVariantId]
    ): ConnectionIO[List[NumericAttributeStoredRow]] =
      (fr"""select master_service_offer_variant_id, attribute_code, value
           |from master_service_offer_variant_numeric_attributes
           |where""".stripMargin ++
        fragments.in(fr"master_service_offer_variant_id", variantIds) ++
        fr"order by master_service_offer_variant_id asc, attribute_code asc")
        .query[NumericAttributeStoredRow]
        .to[List]

    def createTables: ConnectionIO[Unit] =
      for {
        _ <- sql"""create table if not exists master_service_offer_variant_numeric_attributes (
                  |  master_service_offer_variant_id uuid not null,
                  |  attribute_code text not null,
                  |  value numeric not null,
                  |  primary key (master_service_offer_variant_id, attribute_code),
                  |  constraint master_service_offer_variant_numeric_attributes_variant_fk
                  |    foreign key (master_service_offer_variant_id) references master_service_offer_variants(id)
                  |) without oids
                  |""".stripMargin.update.run
      } yield ()

    def load(variantId: MasterServiceOfferVariantId): ConnectionIO[Either[QueryFailure, MasterServiceOfferVariantAdditionalAttributes]] =
      for {
        numericAttributes <- sql"""select attribute_code, value
                                  |from master_service_offer_variant_numeric_attributes
                                  |where master_service_offer_variant_id = $variantId
                                  |order by attribute_code asc
                                  |""".stripMargin.query[NumericAttributeRow].to[List]
      } yield decodeStoredNumericAttributes("load-master-service-offer-variant-attributes", loadRowsForVariant(numericAttributes))

    def loadMany(
      variantIds: List[MasterServiceOfferVariantId]
    ): ConnectionIO[Either[QueryFailure, Map[MasterServiceOfferVariantId, MasterServiceOfferVariantAdditionalAttributes]]] =
      NonEmptyList.fromList(variantIds) match {
        case Some(ids) =>
          for {
            numericRows <- selectNumericAttributes(ids)
          } yield mergeLoadedAttributes("load-many-master-service-offer-variant-attributes", variantIds, numericRows)
        case None =>
          FC.pure(Right(Map.empty))
      }

    def replace(
      variantId: MasterServiceOfferVariantId,
      attributes: MasterServiceOfferVariantAdditionalAttributes,
    ): ConnectionIO[Unit] =
      for {
        _ <- sql"""delete from master_service_offer_variant_numeric_attributes
                  |where master_service_offer_variant_id = $variantId
                  |""".stripMargin.update.run
        _ <- insertNumericAttributes(
          attributes.intAttributes.toList.map {
            case (attributeCode, value) =>
              (variantId, attributeCode, BigDecimal(value))
          } ++ attributes.bigDecimalAttributes.toList.map {
            case (attributeCode, value) =>
              (variantId, attributeCode, value)
          } ++ attributes.enumAttributes.toList.map {
            case (attributeCode, value) =>
              (variantId, attributeCode, BigDecimal(value.intCode))
          } ++ attributes.booleanAttributes.toList.map {
            case (attributeCode, value) =>
              (variantId, attributeCode, encodeBooleanValue(value))
          }
        )
      } yield ()

    private def insertNumericAttributes(rows: List[NumericAttributeInsertRow]): ConnectionIO[Int] =
      if (rows.isEmpty) {
        FC.pure(0)
      } else {
        Update[NumericAttributeInsertRow](
          """insert into master_service_offer_variant_numeric_attributes (
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
