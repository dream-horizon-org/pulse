Store a piece of knowledge in the memory graph.

1. Ask what to remember if not provided
2. Search for existing entities with `search_nodes` to avoid duplicates
3. If a matching entity exists: use `add_observations` to append the new fact
4. If no matching entity exists: use `create_entities` with the appropriate type
5. If it relates to another entity: use `create_relations` to link them
6. Confirm what was stored

Entity types: service, database, component, table, decision, bug, preference, tool, concept, person
Relation types: depends_on, uses, contains, queries, exports_to, consumes_from, broke_because_of, was_fixed_by, prefers
