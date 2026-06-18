AI-ENTRYPOINT
LOCAL LLM REFERENCE
OFFLINE TAPIR HTTP MIGRATION DOCS
Read this file before migrating additional `bifunctor-tagless` HTTP slices to Tapir

# LOCAL LLM REFERENCE: Tapir in `bifunctor-tagless`

The current HTTP adapter pattern is:

- pure endpoint contracts in `leaderboard/http/tapir/*TapirEndpoints.scala`
- thin `HttpApi[F]` adapters in `leaderboard.api.*Api`
- direct route construction with the default `Http4sServerInterpreter`
- endpoint singleton bindings and API bindings in Distage modules
- route aggregation through `Set[HttpApi[F]]` and `HttpServer`

All current API adapters follow this shape:

```scala
final class SliceApi[F[+_, +_]: Error2](
  dependency: SliceDependency[F],
  tapirEndpoints: SliceTapirEndpoints,
)(implicit
  async: Async[F[Throwable, _]]
) extends HttpApi[F] {
  def http: HttpRoutes[F[Throwable, _]] =
    Http4sServerInterpreter[F[Throwable, _]]().toRoutes {
      import tapirEndpoints.*
      List(
        endpoint.serverLogic[F[Throwable, _]](...)
      )
    }
}
```

The API adapter owns server-logic assembly and calls the interpreter directly. There is no separate route-interpreter binding in the application graph.

## Current slices

The direct-interpreter pattern is used by:

- `LadderApi`
- `CategoryApi`
- `ServiceApi`
- `MasterApi`
- `MasterLocationApi`
- `MasterServiceOfferApi`
- `MasterServiceOfferVariantApi`
- `ProfileApi`
- `BeautySearchApi`

There is currently no OpenAPI/Swagger route in `distage-example`. If one is added later, it should consume the canonical endpoint collections from `*TapirEndpoints` rather than manually reconstructing endpoint sets.

## Contract preservation

Route-level HTTP contract tests are the source of truth.

The adapters use default Tapir/http4s interpreter behavior:

- path, query, and body decode failures return Tapir's default `400 BadRequest`
- Tapir validator failures return the same default `400 BadRequest`
- endpoint-domain failures continue through each endpoint's declared error output
- uncaught server-logic failures retain the default interpreter behavior

Do not add custom decode-failure, exception, or reject handlers without an explicit contract task.

Important preserved route details include:

- `/category/root` is a literal successful route
- malformed UUID path captures return `400 BadRequest`
- Beauty search backend/query failures remain endpoint-domain failures
- Beauty search semantic validators use default Tapir validation responses

## Migration checklist

1. Add or confirm route-level contract tests.
2. Define pure contracts in `leaderboard/http/tapir/*TapirEndpoints.scala`.
3. Keep the public adapter as `leaderboard.api.<Slice>Api` implementing `HttpApi[F]`.
4. Assemble server logic in the adapter.
5. Construct routes with `Http4sServerInterpreter[F[Throwable, _]]().toRoutes(...)`.
6. Bind the endpoint singleton and API implementation in the relevant Distage module.
7. Preserve weak `many[HttpApi[F]]` membership.
8. Run the focused API contract suites.

## Boundaries

- Do not replace `HttpServer`.
- Do not move business logic into endpoint definitions.
- Do not introduce a cross-project error ADT for transport migration.
- Do not add custom interpreter handlers without explicit contract requirements.
- Do not change endpoint paths, JSON models, validation, or error semantics as incidental cleanup.
