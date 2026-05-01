---
name: review
description: Run code-review-architect, code-tidier, and consistency-auditor agents in parallel to analyze recent changes for architectural quality, cleanup opportunities, and convention adherence.
user-invocable: true
argument-hint: [description of changes]
---

# Review Changes

Run all three agents in parallel using the Agent tool in a single message. All agents are **read-only** — they must only report findings, never modify files.

1. **code-review-architect**: Evaluate architectural quality, edge cases, design fit, and pattern adherence of the recent changes. If $ARGUMENTS is provided, include it as context for the review. Do not modify any files — report findings only.

2. **code-tidier**: Review the changed files for unused imports, dead code, simplification opportunities, and consistency with surrounding patterns. Do not modify any files — report findings only.

3. **consistency-auditor**: Audit the changed files against documented project conventions, API contracts, and design patterns from CLAUDE.md and project memory. Flag any violations with file, line number, and the specific convention broken. Do not modify any files — report findings only.

After all agents complete, summarize the key findings from each.
