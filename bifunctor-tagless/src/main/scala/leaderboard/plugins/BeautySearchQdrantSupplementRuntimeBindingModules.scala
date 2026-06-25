package leaderboard.plugins

import distage.ModuleDef
import leaderboard.search.BeautySearchBackend
import leaderboard.search.document.BeautySearchReadyCatalogDocuments
import leaderboard.search.dsl.{BeautySearchSpec, VectorSearchSpec}
import leaderboard.search.elasticsearch.{ElasticsearchJsonClient, ElasticsearchSearchBackend}
import leaderboard.search.embedding.EmbeddingClient
import leaderboard.search.qdrant.{QdrantSearchClient, QdrantSemanticCandidateBackend, QdrantSemanticCandidateSearch}
import leaderboard.search.semantic.{InMemoryVariantSearchDocumentLookup, SemanticCandidateBackend, VariantSearchDocumentLookup}
import leaderboard.seed.BeautyQSeedLoader
import zio.IO

// QP13: the smallest production runtime binding module that closes the exact three supplement
// runtime bindings the QP10 launcher seam / QP4 activation module deliberately leave open (the
// `READY_LAUNCHER_BINDINGS_BLOCKED` boundary reported by QP12):
//
//   - `BeautySearchBackend[IO] @Id("qdrantSupplementLexicalElasticsearch")`
//   - `SemanticCandidateBackend[IO]`
//   - `VariantSearchDocumentLookup[IO]`
//
// Each is bound to an existing, source-confirmed implementation:
//
//   - the qualified lexical backend is the existing ES-backed `ElasticsearchSearchBackend` over the
//     same `BeautySearchSpec` the opt-in module binds (NOT in-memory search, NOT Qdrant), so the
//     default ES-only behaviour is preserved for callers that never include this module;
//   - the semantic backend is the existing `QdrantSemanticCandidateBackend` over
//     `QdrantSemanticCandidateSearch` (embedding -> Qdrant points/search); it appends candidates
//     only, with no score fusion / reranking and no routing of residual text to Qdrant;
//   - the document lookup is the existing `InMemoryVariantSearchDocumentLookup` resolving Qdrant
//     candidate ids (`MasterServiceOfferVariantId`) against the ready seed catalog -- no invented
//     domain-id/Qdrant-id translation; Qdrant-compatible point-id rules are unchanged.
//
// This module binds no Qdrant/ES/Llama leaf I/O client, performs no readiness HTTP call, starts no
// indexing, registers no config, and does not change the default `/beauty-search` route. It depends
// on the leaf collaborators (`ElasticsearchJsonClient`, `EmbeddingClient`, `QdrantSearchClient`) and
// the seed loader being supplied by whichever module assembles it, exactly as the opt-in module's
// own documentation states. It is NOT included by `LeaderboardPlugin` default modules, so absent
// env / `es-only-rollback` graphs never gain a Qdrant edge.
object BeautySearchQdrantSupplementRuntimeBindingModules {
  def supplementRuntimeBindings(vectorSearchSpec: VectorSearchSpec): ModuleDef = new ModuleDef {
    // Lexical leg: the qualified ES-backed Beauty search backend.
    make[BeautySearchBackend[IO]].named("qdrantSupplementLexicalElasticsearch").from {
      (spec: BeautySearchSpec, client: ElasticsearchJsonClient) =>
        new ElasticsearchSearchBackend(spec, client)
    }

    // Semantic leg: the existing Qdrant semantic candidate backend (append-only, no fusion).
    make[SemanticCandidateBackend[IO]].from {
      (embeddingClient: EmbeddingClient, qdrantSearchClient: QdrantSearchClient) =>
        new QdrantSemanticCandidateBackend(
          new QdrantSemanticCandidateSearch(embeddingClient, qdrantSearchClient),
          vectorSearchSpec,
        )
    }

    // Document lookup: resolve Qdrant candidate variant ids against the ready seed catalog.
    make[VariantSearchDocumentLookup[IO]].from {
      (ready: BeautySearchReadyCatalogDocuments) =>
        new InMemoryVariantSearchDocumentLookup[IO](ready.documents)
    }

    make[BeautySearchReadyCatalogDocuments].from {
      (loader: BeautyQSeedLoader) =>
        BeautySearchCatalogBackendFactory.fromSeedLoader(loader) match {
          case Right(value) => value
          case Left(error)  => throw new IllegalStateException(error.message)
        }
    }
  }
}
