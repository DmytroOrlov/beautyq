package leaderboard.search.beautyq.gen2.materialization

import izumi.functional.bio.Error2
import leaderboard.search.beautyq.gen2.contract.VariantSearchDocumentGen2
import leaderboard.search.gen2.core.materialization.{MaterializedSearchDocuments, SearchMaterializationError, SearchMaterializer, SearchSnapshotSource}

type MaterializedBeautyQVariantDocuments =
  MaterializedSearchDocuments[BeautyQSearchSnapshot, VariantSearchDocumentGen2]

type BeautyQMaterializationError =
  SearchMaterializationError[SnapshotLoadError, BeautyQVariantProjectionErrors]

object BeautyQMaterializationError {
  export SearchMaterializationError.{Projection, Snapshot}
}

trait BeautyQVariantMaterializer[F[_, _]] {
  def load: F[BeautyQMaterializationError, MaterializedBeautyQVariantDocuments]
}

/** Thin BeautyQ binding over the generic [[SearchMaterializer]]: supplies the BeautyQ projection,
  * projected-documents fingerprint and projection format version. Result and error types are aliases
  * of the generic kernel types, so this boundary adds no field-for-field compatibility mapping.
  */
object BeautyQVariantMaterializer {
  final class FromSnapshotSource[F[+_, +_]: Error2](
    source: SearchSnapshotSource[F, SnapshotLoadError, BeautyQSearchSnapshot]
  ) extends BeautyQVariantMaterializer[F] {
    def load: F[BeautyQMaterializationError, MaterializedBeautyQVariantDocuments] =
      SearchMaterializer
        .materialize[F, SnapshotLoadError, BeautyQSearchSnapshot, BeautyQVariantProjectionErrors, VariantSearchDocumentGen2](
          source = source,
          project = BeautyQVariantProjectionGen2.project,
          fingerprint = BeautyQProjectedDocumentsFingerprint.compute,
          projectionFormatVersion = BeautyQVariantProjectionGen2.projectionFormatVersion,
        )
  }
}
