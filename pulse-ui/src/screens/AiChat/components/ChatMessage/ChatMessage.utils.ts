const SQL_BLOCK_REGEX = /```sql\n([\s\S]*?)```/g;

export function extractSql(text: string): string | null {
  const match = SQL_BLOCK_REGEX.exec(text);
  SQL_BLOCK_REGEX.lastIndex = 0;
  return match ? match[1].trim() : null;
}

export function stripSqlBlocks(text: string): string {
  return text.replace(SQL_BLOCK_REGEX, "").trim();
}
