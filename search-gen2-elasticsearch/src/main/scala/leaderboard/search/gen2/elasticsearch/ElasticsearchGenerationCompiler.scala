package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.core.materialization.{ContentFingerprint, MaterializedSearchDocuments, ProjectedDocumentsFingerprint, ProjectionFormatVersion}
import leaderboard.search.gen2.core.plan.ContractFingerprint

/** The complete backend-specific reuse identity for one Elasticsearch generation: matching source,
  * projected-document, contract, projection-format, compiler and index-format identity together are what
  * license physical index reuse (Brick 5C); matching source/contract fingerprints alone are never
  * sufficient. */
final case class ElasticsearchGenerationIdentity(
  sourceContentFingerprint: ContentFingerprint,
  projectedDocumentsFingerprint: ProjectedDocumentsFingerprint,
  contractFingerprint: ContractFingerprint,
  projectionFormatVersion: ProjectionFormatVersion,
  compilerVersion: ElasticsearchCompilerVersion,
  indexFormatVersion: ElasticsearchIndexFormatVersion,
)

sealed trait ElasticsearchGenerationCompileError

object ElasticsearchGenerationCompileError {
  final case class Mapping(error: ElasticsearchMappingError) extends ElasticsearchGenerationCompileError
  final case class Document(error: ElasticsearchDocumentCompileError) extends ElasticsearchGenerationCompileError
}

type CompiledElasticsearchGeneration[Document, Id] = ElasticsearchGenerationCompiler.CompiledElasticsearchGeneration[Document, Id]

/** The one production entry point binding a validated complete [[ElasticsearchPolicy]] and a
  * [[MaterializedSearchDocuments]] snapshot into one immutable [[CompiledElasticsearchGeneration]]: it
  * derives `policy.index` internally and calls [[ElasticsearchMappingCompiler.compile]] and
  * [[ElasticsearchDocumentCompiler.compile]] exactly once each, over the exact same index policy and
  * materialized documents, and derives the complete [[ElasticsearchGenerationIdentity]] directly from
  * those inputs - a caller cannot independently supply a different declaration, contract fingerprint,
  * projected fingerprint, or independently compiled mapping/document vector. `contractFingerprint` is the
  * one complete policy fingerprint (mapping, analyzers and query-affecting choices together) - the same
  * value a domain's own cursor-bound plan identity and `ElasticsearchSearchRequestCompiler`
  * consume. */
object ElasticsearchGenerationCompiler {

  def compile[Snapshot, Document, Id](
    policy: ElasticsearchPolicy[Document, Id],
    materialized: MaterializedSearchDocuments[Snapshot, Document],
  ): Either[ElasticsearchGenerationCompileError, CompiledElasticsearchGeneration[Document, Id]] =
    ElasticsearchMappingCompiler.compile(policy.index) match {
      case Left(error) => Left(ElasticsearchGenerationCompileError.Mapping(error))
      case Right(mapping) =>
        ElasticsearchDocumentCompiler.compile(policy.index, materialized.documents) match {
          case Left(error) => Left(ElasticsearchGenerationCompileError.Document(error))
          case Right(documents) =>
            val identity =
              ElasticsearchGenerationIdentity(
                sourceContentFingerprint = materialized.sourceSnapshot.contentFingerprint,
                projectedDocumentsFingerprint = materialized.projectedDocumentsFingerprint,
                contractFingerprint = policy.contractFingerprint,
                projectionFormatVersion = materialized.projectionFormatVersion,
                compilerVersion = policy.index.compilerVersion,
                indexFormatVersion = policy.index.indexFormatVersion,
              )
            Right(new CompiledElasticsearchGeneration[Document, Id](mapping, documents, identity))
        }
    }

  /** Final read-only result owned by this compiler. A private constructor and no companion factory make
    * this compiler the only production construction path, so mapping, documents and identity can never be
    * paired from two separate compilations. Not a case class: no public `copy`, and `final` forbids
    * subclassing. */
  final class CompiledElasticsearchGeneration[Document, Id] private[ElasticsearchGenerationCompiler] (
    val mapping: ElasticsearchMapping,
    val documents: Vector[ElasticsearchIndexedDocument],
    val identity: ElasticsearchGenerationIdentity,
  )
}
