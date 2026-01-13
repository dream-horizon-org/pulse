# Get Tables and Columns API

## Get All Tables and Columns

Returns a list of all tables in the Athena database along with their column metadata.

**Endpoint:** `GET /query/tables`

**Headers:**
- `Accept: application/json`
- `Authorization: Bearer <token>` (if authentication is required)

**Response:**
```json
{
  "data": [
    {
      "tableName": "table1",
      "tableSchema": "pulse_athena_db",
      "tableType": "BASE TABLE",
      "columns": [
        {
          "columnName": "id",
          "dataType": "varchar",
          "ordinalPosition": 1,
          "isNullable": "NO"
        },
        {
          "columnName": "name",
          "dataType": "varchar",
          "ordinalPosition": 2,
          "isNullable": "YES"
        }
      ]
    },
    {
      "tableName": "table2",
      "tableSchema": "pulse_athena_db",
      "tableType": "BASE TABLE",
      "columns": []
    }
  ],
  "error": null
}
```

### cURL Example

```bash
curl --location 'http://localhost:8080/query/tables' \
  --header 'Accept: application/json' \
  --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJkZXYtdXNlciIsImVtYWlsIjoiZGV2LXVzZXJAbG9jYWxob3N0LmxvY2FsIiwibmFtZSI6IkRldmVsb3BtZW50IFVzZXIiLCJ0eXBlIjoiYWNjZXNzIiwiaWF0IjoxNzY3NzgxNjIxLCJleHAiOjE3Njc4NjgwMjF9.BHFrH5H8YP-jxiTyxmU5aPypaWTKlZ7E5kSo0ES5Em4'
```

### Notes

- This endpoint queries the `information_schema` tables in Athena to retrieve metadata
- The response includes all tables in the configured database along with their column information
- Tables are sorted alphabetically by name
- Columns are sorted by their ordinal position
- The endpoint does not require a `user-email` header as it returns database metadata, not user-specific data

