Create a JIRA ticket in the IA project based on the user's description: $ARGUMENTS

## Instructions

1. **Fetch existing epics** from JIRA project IA to find a good parent epic for this ticket. Known epics for reference (verify current state):
   - Abundance (IA-70) - Economy/macro
   - Strategy (IA-53) - Strategy selection/detection
   - Micro God (IA-40) - Unit micro/combat
   - Scouting (IA-50) - Scouting behavior
   - Sim City (IA-11) - Building placement
   - Map Analysis (IA-143) - Map data/pathfinding
   - Overmind Restructuring (IA-105) - Architecture refactors
   - CI/CD (IA-13) - Build/deploy pipeline
   - Build Orders V2 (IA-1) - Build order system
   - Build Reactions (IA-147) - Reactive build adjustments

2. **Determine epic fit**: Based on the ticket description, pick the best-fit epic. If no existing epic is a good match, present the user with 2-3 suggestions for a new epic name before creating the ticket.

3. **Create the ticket** using the Atlassian MCP tools:
   - Project: IA
   - Issue type: Task (or Story/Bug as appropriate from context)
   - Summary: concise title derived from the description
   - Description: expand the user's description into a clear ticket body
   - Do NOT set status — new tickets default to NEEDS TRIAGE which is correct

4. **Link to epic**: After creating the ticket, link it to the chosen epic using an issue link.

5. **Report back** with the ticket key, summary, and which epic it was assigned to.

## Atlassian Config
- Cloud ID: `820f92aa-673d-4648-a10b-d62d0bcb3fb1`
- Project key: IA
