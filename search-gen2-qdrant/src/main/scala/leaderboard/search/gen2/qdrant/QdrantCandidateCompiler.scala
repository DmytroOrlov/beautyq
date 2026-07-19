package leaderboard.search.gen2.qdrant

import io.circe.Json
import leaderboard.search.gen2.contract.*

sealed trait QdrantCandidateCompileError
object QdrantCandidateCompileError {
  final case class Constraint(index: Int, message: String) extends QdrantCandidateCompileError
  final case class EmptyTerms(index: Int, fieldId: FieldId) extends QdrantCandidateCompileError
  final case class ValueEncoding(index: Int, fieldId: FieldId, error: QdrantPayloadError) extends QdrantCandidateCompileError
  final case class UndeclaredField(index: Int, fieldId: FieldId) extends QdrantCandidateCompileError
  final case class UnindexedField(index: Int, fieldId: FieldId) extends QdrantCandidateCompileError
  final case class Embedding(error: QdrantEmbeddingError) extends QdrantCandidateCompileError
  final case class EmbeddingInputMismatch(expected: String, actual: String) extends QdrantCandidateCompileError
  final case class EmbeddingModelMismatch(expected: QdrantEmbeddingModelIdentity, actual: QdrantEmbeddingModelIdentity) extends QdrantCandidateCompileError
}

/** Pure target-free candidate query prepared from one CandidatePlan and one QdrantPolicy. */
final class QdrantPreparedCandidateQuery private[qdrant] (
  val policy: QdrantPolicy[?, ?],
  val embeddingInput: QdrantEmbeddingInput,
  val filter: Vector[Json],
  val limit: Int,
  val candidateContractFingerprint: String,
)

/** Completed request body. Collection authorization and URL/path binding belong to Brick 6B. */
final class QdrantCompiledCandidateRequest private[qdrant] (
  val body: Json,
  val limit: Int,
  val candidateContractFingerprint: String,
)

object QdrantCandidateRequestCompiler {
  def prepare[Document, Id](
    policy: QdrantPolicy[Document, Id],
    plan: CandidatePlan[Document],
  ): Either[QdrantCandidateCompileError, QdrantPreparedCandidateQuery] =
    for {
      filter <- sequence(plan.hardConstraints.zipWithIndex.map { case (constraint, index) =>
                   PlannedConstraint.validate(constraint) match {
                     case Left(errors) => Left(QdrantCandidateCompileError.Constraint(index, errors.toVector.mkString("; ")))
                     case Right(valid) => compileConstraint(policy, valid, index)
                   }
                 }).map(_.flatten)
      limit  <- policy.retrieval.limit.left.map(error => QdrantCandidateCompileError.Constraint(-1, error.toString))
      input  <- QdrantEmbeddingInput.from(
                  QdrantEmbeddingPurpose.CandidateQuery,
                  "candidate-query",
                  plan.semanticText.value,
                  policy.embeddingModel,
                ).left.map(QdrantCandidateCompileError.Embedding.apply)
    } yield new QdrantPreparedCandidateQuery(policy, input, filter, limit, policy.candidateContractFingerprint.value)

  def complete(
    prepared: QdrantPreparedCandidateQuery,
    embedding: QdrantEmbeddingResult,
  ): Either[QdrantCandidateCompileError, QdrantCompiledCandidateRequest] = {
    val policy = prepared.policy
    if (embedding.inputFingerprint != prepared.embeddingInput.fingerprint) Left(QdrantCandidateCompileError.EmbeddingInputMismatch(prepared.embeddingInput.fingerprint, embedding.inputFingerprint))
    else if (embedding.model != prepared.embeddingInput.modelValue) Left(QdrantCandidateCompileError.EmbeddingModelMismatch(prepared.embeddingInput.modelValue, embedding.model))
    else {
      val query = Json.fromValues(embedding.values.map(value => Json.fromBigDecimal(BigDecimal(value))))
      val common = Vector(
        "query" -> query,
        "using" -> Json.fromString(policy.vectorName.value),
        "limit" -> Json.fromInt(prepared.limit),
        "with_payload" -> Json.fromBoolean(false),
        "with_vector" -> Json.fromBoolean(false),
      )
      val withThreshold = policy.retrieval.scoreThreshold.map(value => "score_threshold" -> Json.fromBigDecimal(BigDecimal(value))).toVector
      val withFilter = if (prepared.filter.isEmpty) Vector.empty else Vector("filter" -> Json.obj("must" -> Json.fromValues(prepared.filter)))
      Right(new QdrantCompiledCandidateRequest(Json.obj((common ++ withThreshold ++ withFilter)*), prepared.limit, prepared.candidateContractFingerprint))
    }
  }

  private def compileConstraint[Document, Id](
    policy: QdrantPolicy[Document, Id],
    constraint: PlannedConstraint[Document],
    index: Int,
  ): Either[QdrantCandidateCompileError, Vector[Json]] = constraint match {
    case terms: PlannedConstraint.Terms[Document, ?] =>
      validateField(policy, terms.field, index).flatMap { _ =>
        val values = terms.values.toVector.map(terms.field.codec.encodeCanonical).sorted
        if (values.isEmpty) Left(QdrantCandidateCompileError.EmptyTerms(index, terms.field.id))
        else sequence(values.map(value => QdrantPayloadCompiler.encodeCanonical(terms.field, value).left.map(error => QdrantCandidateCompileError.ValueEncoding(index, terms.field.id, error)))).map { encoded =>
          val matchJson = encoded match {
            case Vector(single) => Json.obj("value" -> single)
            case many           => Json.obj("any" -> Json.fromValues(many))
          }
          Vector(Json.obj("key" -> Json.fromString(terms.field.path.value), "match" -> matchJson))
        }
      }
    case range: PlannedConstraint.NumberRange[Document, ?] =>
      validateField(policy, range.field, index).flatMap(_ => compileRange(range.field, range.bounds, index))
    case overlap: PlannedConstraint.IntervalOverlap[Document, ?] =>
      for {
        _       <- validateField(policy, overlap.from, index)
        _       <- validateField(policy, overlap.to, index)
        toJson  <- compileBound(overlap.to, overlap.bounds.lower, isLower = true, index)
        fromJson <- compileBound(overlap.from, overlap.bounds.upper, isLower = false, index)
      } yield {
        val values = Vector(
          toJson.map(entry => Json.obj("key" -> Json.fromString(overlap.to.path.value), "range" -> Json.obj(entry))),
          fromJson.map(entry => Json.obj("key" -> Json.fromString(overlap.from.path.value), "range" -> Json.obj(entry))),
        ).flatten
        values
      }
    case geo: PlannedConstraint.GeoDistanceFilter[Document] =>
      validateField(policy, geo.field, index).map { _ =>
        Vector(Json.obj(
          "key" -> Json.fromString(geo.field.path.value),
          "geo_radius" -> Json.obj(
            "center" -> Json.obj("lat" -> Json.fromBigDecimal(geo.origin.lat), "lon" -> Json.fromBigDecimal(geo.origin.lon)),
            "radius" -> Json.fromBigDecimal(geo.radius.meters),
          ),
        ))
      }
  }

  private def compileRange[Document, A](
    field: SearchField[Document, A],
    bounds: RangeBounds[A],
    index: Int,
  ): Either[QdrantCandidateCompileError, Vector[Json]] =
    for {
      lower <- compileBound(field, bounds.lower, isLower = true, index)
      upper <- compileBound(field, bounds.upper, isLower = false, index)
    } yield {
      val entries = Vector(lower, upper).flatten
      if (entries.isEmpty) Vector.empty else Vector(Json.obj("key" -> Json.fromString(field.path.value), "range" -> Json.obj(entries*)))
    }

  private def compileBound[Document, A](
    field: SearchField[Document, A],
    bound: Bound[A],
    isLower: Boolean,
    index: Int,
  ): Either[QdrantCandidateCompileError, Option[(String, Json)]] = bound match {
    case Bound.Unbounded => Right(None)
    case Bound.Inclusive(value) => QdrantPayloadCompiler.encodeCanonical(field, field.codec.encodeCanonical(value)).left.map(error => QdrantCandidateCompileError.ValueEncoding(index, field.id, error)).map(json => Some((if (isLower) "gte" else "lte", json)))
    case Bound.Exclusive(value) => QdrantPayloadCompiler.encodeCanonical(field, field.codec.encodeCanonical(value)).left.map(error => QdrantCandidateCompileError.ValueEncoding(index, field.id, error)).map(json => Some((if (isLower) "gt" else "lt", json)))
  }

  private def validateField[Document, Id](
    policy: QdrantPolicy[Document, Id],
    field: SearchField[Document, ?],
    index: Int,
  ): Either[QdrantCandidateCompileError, Unit] =
    if (!policy.declaration.allFields.exists(_ eq field)) Left(QdrantCandidateCompileError.UndeclaredField(index, field.id))
    else if (field.capabilities.filterOperators.nonEmpty && !policy.payloadIndexes.exists(_.field eq field)) Left(QdrantCandidateCompileError.UnindexedField(index, field.id))
    else Right(())

  private def sequence[A](values: Vector[Either[QdrantCandidateCompileError, A]]): Either[QdrantCandidateCompileError, Vector[A]] =
    values.foldLeft[Either[QdrantCandidateCompileError, Vector[A]]](Right(Vector.empty)) {
      case (acc, next) => acc.flatMap(done => next.map(value => done :+ value))
    }
}
