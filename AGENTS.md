# Agent Guidelines

## Path Restrictions & Sensitive Data
- **Forbidden paths & strings**:
  - Never use absolute local user paths (anything under `/home/<user>/`) anywhere in the codebase, tests, comments, or documentation.
  - Never use the name of the author's private media database directory (the all-caps directory name under the author's media root) in test paths, filenames, or examples. Use generic paths instead (e.g. `~/Series-Shows/...`, `~/Movies/...`, or mock directories).
  - Avoid copyrighted movie or TV show names in strings, comments, tests, etc.
- **Path conventions**:
  - Always use sanitized relative or mock paths such as `~/Series-Shows/` instead of real absolute media paths.
