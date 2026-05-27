Show the current state of the knowledge graph memory.

1. Call `read_graph` via the `project-0-pulse-memory` MCP to get all entities and relations
2. Group entities by `entityType` (decisions, bugs, services, components, etc.)
3. For each entity, show its name and observations as bullet points
4. Show all relations as a table: From → Relation → To
5. If the graph is empty, say so and suggest what kinds of things to store
