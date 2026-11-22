---
paths:
  - "**/db/migration/**"
  - "**/domain/**/*.java"
  - "**/infrastructure/**/*.java"
---

# Database and migrations

- Schema is managed exclusively by Flyway. Hibernate only validates it: `spring.jpa.hibernate.ddl-auto: validate`.
- All migrations live in `cm-app/src/main/resources/db/migration` (one ordered history for the whole monolith).
- **Never modify an applied migration.** Every schema change = a new `V<n>__<description>.sql` file.
- Integrity constraints (UNIQUE, FK, CHECK) are defined in the database, not only in code.
