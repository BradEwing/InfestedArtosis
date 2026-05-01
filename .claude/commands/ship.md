Commit, push, and open a PR for the current branch.

## Instructions

### 1. Analyze Changes

Run `git diff --cached --stat` and `git diff --stat` to see what's staged and unstaged. If nothing is staged, stage all changes with `git add -A`.

Run `git diff --cached` to review the actual diff content for writing the commit message.

### 2. Determine the Commit Message

Follow **Conventional Commits** with Jira issue keys:

```
<type>(IA-<number>): <lowercase description>
```

- **Types:** `feat`, `fix`, `refactor`, `chore`, `build`, `poc`
- **Scope** is the Jira ticket key extracted from the current branch name (e.g., branch `IA-189` -> scope `IA-189`). If the branch has no `IA-` prefix, omit the scope.
- **Description** is lowercase, imperative, concise.
- Infer the type and description from the diff content. If $ARGUMENTS is provided, use it as the description (still infer the type).
- If the changes span multiple concerns, use a multi-line commit: first line is the primary change, subsequent lines describe secondary changes.

### 3. Confirm and Commit

Show the user the proposed commit message and ask for confirmation before committing. If the user suggests changes, incorporate them.

Commit with `git commit -m "<message>"`.

### 4. Push

Push the branch with `git push`. If the upstream is not set, use `git push -u origin HEAD`.

### 5. Open a PR (if needed)

Check if a PR already exists for this branch: `gh pr view --json url 2>/dev/null`.

- **If no PR exists**: Create one with `gh pr create --base main --head <branch> --title "<commit-first-line>" --body "<summary>"`. The body should be a brief summary of the changes (a few sentences max). Do NOT include a Test Plan section.
- **If a PR already exists**: Report the existing PR URL. Do not create a duplicate.

### 6. Report

Output the commit hash, PR URL, and a one-line summary.
