/**
 * Column metadata from the database
 */
export interface ColumnMetadata {
  columnName: string;
  dataType: string;
  ordinalPosition: number;
  isNullable: string;
}

/**
 * Single table metadata from the API
 */
export interface TableMetadata {
  tableName: string;
  tableSchema: string;
  tableType: string;
  columns: ColumnMetadata[];
}

/**
 * Table metadata response from the API - returns array of tables
 */
export type TableMetadataResponse = TableMetadata[];

/**
 * Error response for metadata API
 */
export interface TableMetadataErrorResponse {
  error: {
    message: string;
    cause: string;
  };
  data: null;
  status: number;
}
