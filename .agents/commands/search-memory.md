Search the knowledge graph memory for relevant context.

1. Ask what to search for if not provided
2. Call `search_nodes` via the `project-0-pulse-memory` MCP with the query
3. If results found: display matching entities with their observations and relations
4. If no results: try broader search terms, then report nothing found
5. Suggest related entities the user might want to explore further
