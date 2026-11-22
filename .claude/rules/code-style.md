# Code style

- Code, identifiers, and commit messages are in English.
- No comments in code (including Javadoc, YAML and SQL comments). Code must explain itself:
  precise names for classes, methods, variables, constants and tests; extract a well-named
  method or constant instead of explaining a block. The "why" behind a non-obvious decision
  goes into the commit message, `CLAUDE.md` or a rule file in `.claude/rules`, not into a comment.
- No single-letter or cryptic abbreviated names, in code and in the API (query parameters, JSON fields,
  paths): use meaningful English words (`keyword`, not `q`; `offer`, not `o`). An exception needs a
  conscious decision recorded in this file.
- Keep it simple. No speculative abstractions: introduce an interface only when there is
  more than one implementation or a real testing need.

## Time
- Business dates (`LocalDate`) come from the `Clock` bean in the `app.time-zone` zone (`Europe/Warsaw`), never from
  `LocalDate.now()` without a clock. Bean Validation (`@Future`, custom constraints via `ClockProvider`) uses the
  same clock. `Instant` values do not depend on the zone.
