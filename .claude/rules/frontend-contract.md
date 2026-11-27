---
paths:
  - "cm-app/src/main/java/com/candidate_manager/security/**"
  - "cm-app/src/main/java/com/candidate_manager/web/**"
  - "**/web/**/*.java"
  - "docs/openapi.json"
  - "docker-init-keycloak/**"
  - "**/src/main/resources/application*.yaml"
---

# Frontend contract

- The browser application lives in its own repository. It logs in on the Keycloak login page with the authorization code
  flow and PKCE (`cm-web`, `S256`), never with the password grant, so passwords never reach the frontend or this API.
  It registers users through `POST /api/auth/register/...` and then starts the login with `login_hint`.
- `cm-web` accepts redirects only to `http://localhost:5173/*` (Vite) and to Swagger UI, logs out to the frontend
  (`post.logout.redirect.uris`) and rotates refresh tokens (`revokeRefreshToken`, reuse 0). Production needs its own exact
  redirect URIs and no direct access grants.
- CORS applies to `/api/**`, origins come from `app.cors.allowed-origins` (local: `http://localhost:5173`, default: none,
  i.e. same-origin deployment). Bearer tokens, no cookies, so credentials are not allowed; `Location` is exposed.
- The API is described with springdoc, only for `/api/**`. `/v3/api-docs` and Swagger UI are enabled in the `local`
  profile only. Public endpoints are listed once in `PublicEndpoint`, used by both `SecurityConfig` and the description.
- `docs/openapi.json` is the contract the frontend generates its types from. `OpenApiContractIntegrationTest` fails when
  the API changes without it: run the tests with `-Dopenapi.snapshot.update=true` and commit the file together with the
  change. Read the diff of the file: it is the change the frontend sees.
- Keep the description faithful: search criteria are bound as flat query parameters (`default-flat-param-object`),
  creating endpoints declare `@ResponseStatus(HttpStatus.CREATED)`, every operation has a `default` response with the
  `ProblemDetail` schema (`application/problem+json`).
