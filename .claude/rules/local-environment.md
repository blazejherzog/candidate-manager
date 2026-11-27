---
paths:
  - "docker-compose.yaml"
  - "docker-init-keycloak/**"
  - "**/src/main/resources/application*.yaml"
  - "**/pom.xml"
---

# Local environment

- SQL logging is off by default; enable it locally with `--logging.level.org.hibernate.SQL=debug`.
- `cm-realm` is imported from `docker-init-keycloak/cm-realm-realm.json` on Keycloak's first start
  (the file name must be `<realm>-realm.json`). An existing realm is never overwritten: after changing
  the file, delete the realm in the admin console or remove the `keycloak_pgdata` volume.
  Without the current realm the `registrationId` attribute is dropped silently and incomplete registrations cannot
  be cleaned up.
- The `cm-backend` client secret is fixed for local development only: the realm file sets it and
  `application-local.yaml` repeats it. It protects nothing beyond the local Keycloak, whose admin login is in
  `docker-compose.yaml` anyway. `spring-boot:run` activates the `local` profile (`cm-app/pom.xml`);
  the jar does not.
- Outside local development the secret comes from `KEYCLOAK_CLIENT_SECRET`, supplied by the environment
  (secret manager); the realm there is not imported from this file. Without it the application does not start.
  Keep the empty default in `${KEYCLOAK_CLIENT_SECRET:}`: an unresolved placeholder would be bound as literal
  text and pass `@NotBlank`, so a missing secret would only show up as 502 on the first registration.
- Register and log in (local only; `cm-web` allows the password grant for curl):
  ```bash
  curl -X POST localhost:8080/api/auth/register/candidate -H 'Content-Type: application/json' \
    -d '{"username":"anna","email":"anna@example.com","password":"Secret-pass-1","firstName":"Anna","lastName":"Nowak"}'
  curl -d grant_type=password -d client_id=cm-web -d username=anna -d password=Secret-pass-1 \
    localhost:8081/realms/cm-realm/protocol/openid-connect/token
  ```
