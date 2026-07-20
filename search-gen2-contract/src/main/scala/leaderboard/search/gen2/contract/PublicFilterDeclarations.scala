package leaderboard.search.gen2.contract

/** The generic public-filter field view exposed by a declaration tree. The public name is a
  * caller-facing contract; it is deliberately independent from [[SearchField.path]]. */
final case class PublicFilterField[Document](
  name: PublicFieldName,
  fieldHandles: Vector[SearchField[Document, ?]],
  acceptedOperators: Vector[PublicOperator],
)

sealed trait PublicFilterDeclaration[Document] {
  def name: PublicFieldName
  def fieldHandles: Vector[SearchField[Document, ?]]
  def acceptedOperators: Vector[PublicOperator]

  /** Narrows the declaration's public policy. [[PublicFilterRegistry.apply]] validates that the
    * requested operators remain supported by the typed declaration. */
  def withOperators(operators: Vector[PublicOperator]): PublicFilterDeclaration[Document]

  private[contract] def supportedOperators: Vector[PublicOperator]
  private[contract] def decode(input: PublicFilterInput): Either[NonEmptyErrors[PublicFilterError], PublicFilterClause[Document]]
}

object PublicFilterDeclaration {
  def value[Document, A](
    name: PublicFieldName,
    field: SearchField[Document, A],
  ): PublicFilterDeclaration[Document] =
    new Value(name, field, None)

  def ordered[Document, A: Ordering](
    name: PublicFieldName,
    field: SearchField[Document, A],
  ): PublicFilterDeclaration[Document] =
    new Ordered(name, field, summon[Ordering[A]], None)

  def intervalOverlap[Document, A: Ordering](
    name: PublicFieldName,
    from: SearchField[Document, A],
    to: SearchField[Document, A],
  ): PublicFilterDeclaration[Document] =
    new Interval(name, from, to, summon[Ordering[A]], None)

  def geoDistance[Document](
    name: PublicFieldName,
    field: SearchField[Document, GeoPoint],
  ): PublicFilterDeclaration[Document] =
    new GeoDistance(name, field, None)

  private final class Value[Document, A](
    val name: PublicFieldName,
    field: SearchField[Document, A],
    narrowed: Option[Vector[PublicOperator]],
  ) extends PublicFilterDeclaration[Document] {
    val fieldHandles: Vector[SearchField[Document, ?]] = Vector(field)
    def supportedOperators: Vector[PublicOperator] =
      PublicOperator.fromFilterCapabilities(field.capabilities.filterOperators).filter {
        case PublicOperator.Equal | PublicOperator.In => true
        case _                                         => false
      }
    def acceptedOperators: Vector[PublicOperator] = narrowed.getOrElse(supportedOperators)
    def withOperators(operators: Vector[PublicOperator]): PublicFilterDeclaration[Document] =
      new Value(name, field, Some(operators))
    def decode(input: PublicFilterInput): Either[NonEmptyErrors[PublicFilterError], PublicFilterClause[Document]] =
      input.operator match {
        case PublicOperator.Equal =>
          scalar(input).flatMap(raw => decodeCanonical(input.field, field, raw)).map { value =>
            PublicFilterClause.Constraint(PlannedConstraint.Terms(field, Set(value)))
          }
        case PublicOperator.In =>
          many(input).flatMap(values => decodeMany(input.field, field, values)).map { values =>
            PublicFilterClause.Constraint(PlannedConstraint.Terms(field, values.toSet))
          }
        case operator => Left(one(PublicFilterError.WrongPublicValueShape(input.field, operator, "Scalar")))
      }
  }

  private final class Ordered[Document, A](
    val name: PublicFieldName,
    field: SearchField[Document, A],
    ordering: Ordering[A],
    narrowed: Option[Vector[PublicOperator]],
  ) extends PublicFilterDeclaration[Document] {
    val fieldHandles: Vector[SearchField[Document, ?]] = Vector(field)
    def supportedOperators: Vector[PublicOperator] =
      PublicOperator.fromFilterCapabilities(field.capabilities.filterOperators).filter {
        case PublicOperator.Equal | PublicOperator.In | PublicOperator.GreaterThan |
            PublicOperator.GreaterThanOrEqual | PublicOperator.LessThan |
            PublicOperator.LessThanOrEqual | PublicOperator.Between => true
        case _ => false
      }
    def acceptedOperators: Vector[PublicOperator] = narrowed.getOrElse(supportedOperators)
    def withOperators(operators: Vector[PublicOperator]): PublicFilterDeclaration[Document] =
      new Ordered(name, field, ordering, Some(operators))
    def decode(input: PublicFilterInput): Either[NonEmptyErrors[PublicFilterError], PublicFilterClause[Document]] =
      input.operator match {
        case PublicOperator.Equal | PublicOperator.In =>
          new Value(name, field, None).decode(input)
        case PublicOperator.Between =>
          input.value match {
            case PublicFilterValue.BetweenBounds(lowerRaw, upperRaw, lowerInclusive, upperInclusive) =>
              for {
                lower <- decodeCanonical(input.field, field, lowerRaw)
                upper <- decodeCanonical(input.field, field, upperRaw)
                bounds <- boundsFor(input.field, lowerRaw, upperRaw, lower, upper, lowerInclusive, upperInclusive, ordering)
              } yield PublicFilterClause.Constraint(PlannedConstraint.NumberRange(field, bounds))
            case _ => Left(one(PublicFilterError.WrongPublicValueShape(input.field, input.operator, "BetweenBounds")))
          }
        case operator =>
          scalar(input).flatMap(raw => decodeCanonical(input.field, field, raw)).flatMap { value =>
            singleBound(input.field, operator, value).map(bounds => PublicFilterClause.Constraint(PlannedConstraint.NumberRange(field, bounds)))
          }
      }
  }

  private final class Interval[Document, A](
    val name: PublicFieldName,
    from: SearchField[Document, A],
    to: SearchField[Document, A],
    ordering: Ordering[A],
    narrowed: Option[Vector[PublicOperator]],
  ) extends PublicFilterDeclaration[Document] {
    val fieldHandles: Vector[SearchField[Document, ?]] = Vector(from, to)
    def supportedOperators: Vector[PublicOperator] = {
      val bothSupportRange = from.capabilities.filterOperators.contains(FilterOperator.Range) &&
        to.capabilities.filterOperators.contains(FilterOperator.Range)
      if (bothSupportRange) Vector(PublicOperator.GreaterThan, PublicOperator.GreaterThanOrEqual, PublicOperator.LessThan, PublicOperator.LessThanOrEqual, PublicOperator.Between)
      else Vector.empty
    }
    def acceptedOperators: Vector[PublicOperator] = narrowed.getOrElse(supportedOperators)
    def withOperators(operators: Vector[PublicOperator]): PublicFilterDeclaration[Document] =
      new Interval(name, from, to, ordering, Some(operators))
    def decode(input: PublicFilterInput): Either[NonEmptyErrors[PublicFilterError], PublicFilterClause[Document]] =
      input.operator match {
        case PublicOperator.Between =>
          input.value match {
            case PublicFilterValue.BetweenBounds(lowerRaw, upperRaw, lowerInclusive, upperInclusive) =>
              for {
                lower <- decodeCanonical(input.field, from, lowerRaw)
                upper <- decodeCanonical(input.field, from, upperRaw)
                bounds <- boundsFor(input.field, lowerRaw, upperRaw, lower, upper, lowerInclusive, upperInclusive, ordering)
              } yield PublicFilterClause.Constraint(PlannedConstraint.IntervalOverlap(from, to, bounds))
            case _ => Left(one(PublicFilterError.WrongPublicValueShape(input.field, input.operator, "BetweenBounds")))
          }
        case operator =>
          scalar(input).flatMap(raw => decodeCanonical(input.field, from, raw)).flatMap { value =>
            singleBound(input.field, operator, value).map(bounds => PublicFilterClause.Constraint(PlannedConstraint.IntervalOverlap(from, to, bounds)))
          }
      }
  }

  private final class GeoDistance[Document](
    val name: PublicFieldName,
    field: SearchField[Document, GeoPoint],
    narrowed: Option[Vector[PublicOperator]],
  ) extends PublicFilterDeclaration[Document] {
    val fieldHandles: Vector[SearchField[Document, ?]] = Vector(field)
    def supportedOperators: Vector[PublicOperator] =
      if (field.capabilities.filterOperators.contains(FilterOperator.GeoDistance)) Vector(PublicOperator.WithinDistance)
      else Vector.empty
    def acceptedOperators: Vector[PublicOperator] = narrowed.getOrElse(supportedOperators)
    def withOperators(operators: Vector[PublicOperator]): PublicFilterDeclaration[Document] =
      new GeoDistance(name, field, Some(operators))
    def decode(input: PublicFilterInput): Either[NonEmptyErrors[PublicFilterError], PublicFilterClause[Document]] =
      scalar(input).flatMap { raw =>
        SearchValueCodec.bigDecimal.decodeCanonical(raw) match {
          case Left(error) => Left(one(PublicFilterError.InvalidCanonicalValue(input.field, error.typeId, raw, error.message)))
          case Right(meters) if meters <= 0 => Left(one(PublicFilterError.NonPositiveDistance(input.field, raw)))
          case Right(meters) => Right(PublicFilterClause.GeoRadius(field, Distance(meters)))
        }
      }
  }

  private def scalar(input: PublicFilterInput): Either[NonEmptyErrors[PublicFilterError], String] = input.value match {
    case PublicFilterValue.Scalar(value) => Right(value)
    case _ => Left(one(PublicFilterError.WrongPublicValueShape(input.field, input.operator, "Scalar")))
  }

  private def many(input: PublicFilterInput): Either[NonEmptyErrors[PublicFilterError], Vector[String]] = input.value match {
    case PublicFilterValue.Many(values) if values.nonEmpty => Right(values)
    case PublicFilterValue.Many(_) => Left(one(PublicFilterError.EmptyPublicValues(input.field)))
    case _ => Left(one(PublicFilterError.WrongPublicValueShape(input.field, input.operator, "Many")))
  }

  private def decodeCanonical[Document, A](
    name: PublicFieldName,
    field: SearchField[Document, A],
    raw: String,
  ): Either[NonEmptyErrors[PublicFilterError], A] =
    field.codec.decodeCanonical(raw) match {
      case Right(value) => Right(value)
      case Left(error) => Left(one(PublicFilterError.InvalidCanonicalValue(name, error.typeId, raw, error.message)))
    }

  private def decodeMany[Document, A](
    name: PublicFieldName,
    field: SearchField[Document, A],
    values: Vector[String],
  ): Either[NonEmptyErrors[PublicFilterError], Vector[A]] = {
    val decoded = values.zipWithIndex.map { case (raw, index) =>
      decodeCanonical(name, field, raw).left.map(_.toVector.map {
        case PublicFilterError.InvalidCanonicalValue(_, typeId, value, message) => PublicFilterError.InvalidCanonicalValueAt(name, index, typeId, value, message)
        case other => other
      })
    }
    val failures = decoded.collect { case Left(errors) => errors }.flatten
    NonEmptyErrors.fromVector(failures) match {
      case Some(errors) => Left(errors)
      case None =>
        val canonical = decoded.collect { case Right(value) => field.codec.encodeCanonical(value) }
        canonical.find(value => canonical.count(_ == value) > 1) match {
          case Some(value) => Left(one(PublicFilterError.DuplicateCanonicalTerm(name, value)))
          case None => Right(decoded.collect { case Right(value) => value })
        }
    }
  }

  private def singleBound[A](
    name: PublicFieldName,
    operator: PublicOperator,
    value: A,
  ): Either[NonEmptyErrors[PublicFilterError], RangeBounds[A]] = operator match {
    case PublicOperator.GreaterThan        => Right(RangeBounds(Bound.Exclusive(value), Bound.Unbounded))
    case PublicOperator.GreaterThanOrEqual => Right(RangeBounds(Bound.Inclusive(value), Bound.Unbounded))
    case PublicOperator.LessThan           => Right(RangeBounds(Bound.Unbounded, Bound.Exclusive(value)))
    case PublicOperator.LessThanOrEqual    => Right(RangeBounds(Bound.Unbounded, Bound.Inclusive(value)))
    case other => Left(one(PublicFilterError.WrongPublicValueShape(name, other, "Scalar")))
  }

  private def boundsFor[A](
    name: PublicFieldName,
    lowerRaw: String,
    upperRaw: String,
    lower: A,
    upper: A,
    lowerInclusive: Boolean,
    upperInclusive: Boolean,
    ordering: Ordering[A],
  ): Either[NonEmptyErrors[PublicFilterError], RangeBounds[A]] =
    if (ordering.gt(lower, upper)) Left(one(PublicFilterError.InvalidBetweenBounds(name, lowerRaw, upperRaw, "lower bound is greater than upper bound")))
    else if (ordering.equiv(lower, upper) && (!lowerInclusive || !upperInclusive)) Left(one(PublicFilterError.InvalidBetweenBounds(name, lowerRaw, upperRaw, "equal endpoints require both bounds inclusive")))
    else Right(RangeBounds(if (lowerInclusive) Bound.Inclusive(lower) else Bound.Exclusive(lower), if (upperInclusive) Bound.Inclusive(upper) else Bound.Exclusive(upper)))

  private def one(error: PublicFilterError): NonEmptyErrors[PublicFilterError] =
    NonEmptyErrors.fromHead(error, Vector.empty)
}

sealed trait PublicFilterDeclarationError
object PublicFilterDeclarationError {
  final case class DuplicateName(name: PublicFieldName, firstIndex: Int, duplicateIndex: Int) extends PublicFilterDeclarationError
  final case class NoSupportedOperators(name: PublicFieldName, declarationIndex: Int) extends PublicFilterDeclarationError
  final case class UnsupportedOperator(name: PublicFieldName, declarationIndex: Int, operator: PublicOperator, supported: Vector[PublicOperator]) extends PublicFilterDeclarationError
}

/** Generic registry for standard public-filter shapes. The ordered declaration vector is the one
  * source for public inventory, operator policy, lookup and decoding. */
final class PublicFilterRegistry[Document] private (
  declarations: Vector[PublicFilterDeclaration[Document]],
  registry: PublicInputRegistry[PublicFilterInput, PublicFieldName, PublicOperator, PublicFilterClause[Document], PublicFilterError],
) {
  val fields: Vector[PublicFilterField[Document]] =
    declarations.map(declaration => PublicFilterField(declaration.name, declaration.fieldHandles, declaration.acceptedOperators))

  def decode(input: PublicFilterInput): Either[NonEmptyErrors[PublicFilterError], PublicFilterClause[Document]] =
    registry.decode(input)

  def decodeNamed(input: PublicFilterInput): Either[NonEmptyErrors[PublicFilterError], (PublicFieldName, PublicFilterClause[Document])] =
    registry.decodeNamed(input)
}

object PublicFilterRegistry {
  def apply[Document](
    declarations: Vector[PublicFilterDeclaration[Document]],
  ): Either[NonEmptyErrors[PublicFilterDeclarationError], PublicFilterRegistry[Document]] = {
    val policyErrors = declarations.zipWithIndex.flatMap { case (declaration, declarationIndex) =>
      val unsupported = declaration.acceptedOperators.filterNot(declaration.supportedOperators.contains).map { operator =>
        PublicFilterDeclarationError.UnsupportedOperator(declaration.name, declarationIndex, operator, declaration.supportedOperators)
      }
      val empty = if (declaration.acceptedOperators.isEmpty) Vector(PublicFilterDeclarationError.NoSupportedOperators(declaration.name, declarationIndex)) else Vector.empty
      unsupported ++ empty
    }
    val specs = declarations.map { declaration =>
      new PublicInputSpec[PublicFilterInput, PublicFieldName, PublicOperator, PublicFilterClause[Document], PublicFilterError] {
        def name: PublicFieldName = declaration.name
        def acceptedOperators: Vector[PublicOperator] = declaration.acceptedOperators
        def decode(input: PublicFilterInput): Either[NonEmptyErrors[PublicFilterError], PublicFilterClause[Document]] = declaration.decode(input)
      }
    }
    PublicInputRegistry(specs, _.field, _.operator, PublicFilterError.UnknownPublicField.apply, PublicFilterError.UnsupportedPublicOperator.apply) match {
      case Left(errors) =>
        val duplicateErrors = errors.toVector.map {
          case PublicDeclarationError.DuplicateName(name, firstIndex, duplicateIndex) =>
            PublicFilterDeclarationError.DuplicateName(name, firstIndex, duplicateIndex)
        }
        val combined = policyErrors ++ duplicateErrors
        NonEmptyErrors.fromVector(combined) match {
          case Some(e) => Left(e)
          case None    => throw new IllegalStateException("expected combined errors when PublicInputRegistry failed")
        }
      case Right(registry) if policyErrors.nonEmpty =>
        NonEmptyErrors.fromVector(policyErrors) match {
          case Some(e) => Left(e)
          case None    => throw new IllegalStateException("expected policy errors but vector was empty")
        }
      case Right(registry) =>
        Right(new PublicFilterRegistry(declarations, registry))
    }
  }

  def unsafeFrom[Document](declarations: Vector[PublicFilterDeclaration[Document]]): PublicFilterRegistry[Document] =
    apply(declarations) match {
      case Right(registry) => registry
      case Left(errors) => throw new IllegalStateException(s"Invalid public filter declarations: ${errors.toVector.mkString(", ")}")
    }
}
