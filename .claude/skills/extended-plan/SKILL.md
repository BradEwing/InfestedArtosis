---
name: extended-plan
description: Enter plan mode and run parallel research agents (bot-scout, bwapi-researcher, tradeoff-analyzer, code-review-architect) to deeply analyze a Jira ticket or feature request before implementation.
user-invocable: true
argument-hint: <Jira ticket (e.g. IA-123) or feature description>
---

# Extended Plan

You are planning a feature or task for the Infested Artosis codebase. Enter plan mode, gather context, then run parallel research agents to inform the design.

## Process

### 1. Gather Context

Determine what $ARGUMENTS refers to:

- **If it matches a Jira ticket pattern** (e.g. `IA-123`): Fetch the ticket details from Jira using the Atlassian MCP tools. Extract the summary, description, acceptance criteria, and any linked issues.
- **If it's a feature description**: Use it directly as the planning topic.

Call the result `$TOPIC` — a clear statement of what needs to be designed/built.

### 2. Enter Plan Mode

Use the EnterPlanMode tool to switch into plan mode before doing any design work.

### 3. Run Research Agents in Parallel

Launch all four agents simultaneously using the Agent tool:

1. **bot-scout**: Research how other open-source StarCraft BW bots solve the problem described in $TOPIC. Pass $TOPIC as the agent prompt.

2. **bwapi-researcher**: Research relevant BWAPI/JBWAPI APIs, known pitfalls, and community techniques for $TOPIC. Pass $TOPIC as the agent prompt.

3. **tradeoff-analyzer**: Analyze the tradeoffs of implementing $TOPIC in the Infested Artosis codebase. Pass $TOPIC along with any Jira context as the agent prompt.

4. **code-review-architect**: Evaluate the architectural implications of $TOPIC in plan mode. Identify which managers, packages, and patterns are affected, propose where new code should live, and flag any responsibility boundary concerns. Pass $TOPIC along with any Jira context as the agent prompt.

### 4. Synthesize a Plan

After all agents complete, synthesize their findings into a cohesive implementation plan:

#### Context
- What the ticket/feature asks for
- Key Jira details (if applicable): epic, priority, linked issues

#### Research Findings
- Summarize key insights from each agent (bot-scout, bwapi-researcher, tradeoff-analyzer, code-review-architect)
- Highlight areas of agreement and tension between the research

#### Proposed Approach
- Concrete implementation steps
- Files to create or modify
- Key design decisions and why (informed by research)

#### Open Questions
- Decisions that need user input
- Tradeoffs where the research was inconclusive
- Areas that need further investigation

Present the plan and wait for user feedback before proceeding to implementation.
