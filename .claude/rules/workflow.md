# Workflow

- Plan first, then implement. Before creating or changing files, present the proposal
  and wait for approval.
- Make changes locally only and show `git diff` afterwards. **Do not commit or push** —
  the developer commits and pushes personally.
- Do not modify files unrelated to the task.
- Do not add dependencies or infrastructure without justification.
- Never claim tests pass unless they were actually run. Report the real result.
- Commit message format (for reference): `[CM-<n>] Description`.
- Rules that must always apply live in `CLAUDE.md` or in a rule file here without `paths`; rules for one area
  live in a rule file scoped with `paths`. Skills are only for on-demand procedures.
