# Appendix — reproducible **`tools/call`** JSON-RPC skeletons *(stdio harness)*

Use after `yarn build`. Replace placeholders. **Lines below are separate stdin writes** terminated with newline unless your harness bundles stream frames.

Skeleton list:

```json
{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
```

Example call (**`list_projects`**):

```json
{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"list_projects","arguments":{}}}
```

**`list_app_vitals_crash_issues`**

```json
{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"list_app_vitals_crash_issues","arguments":{"projectId":"{PROJECT_READ}","limit":5}}}
```

**`get_alert_metrics`** (needs scope)

```json
{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"get_alert_metrics","arguments":{"projectId":"{PROJECT_READ}","scope":"interaction"}}}
```

**`list_session_replays`**

```json
{"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"list_session_replays","arguments":{"projectId":"{PROJECT_READ}","pageSize":10}}}
```

Parsing notes:

- MCP SDK may prepend **session initialization** handshake depending on revision — Cursor handles this implicitly; bare `stdio` testers might need **`initialize`** first. If **`tools/list`** returns nothing, inspect stderr for MCP protocol errors.

