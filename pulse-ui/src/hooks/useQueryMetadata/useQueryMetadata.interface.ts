/**
 * Column metadata from the database
 */
export interface ColumnMetadata {
  name: string;
  type: string;
}

/**
 * Table metadata response from the API
 */
export interface TableMetadataResponse {
  databaseName: string;
  tableName: string;
  columns: ColumnMetadata[];
}

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

