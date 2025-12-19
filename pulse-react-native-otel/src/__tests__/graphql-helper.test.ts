import {
  isGraphQLRequest,
  updateAttributesWithGraphQLData,
} from '../network-interceptor/graphql-helper';

describe('graphql-helper', () => {
  describe('isGraphQLRequest', () => {
    describe('valid GraphQL URLs', () => {
      it('detects GraphQL in URL path', () => {
        expect(isGraphQLRequest('https://api.example.com/graphql')).toBe(true);
      });

      it('detects GraphQL in URL path (case insensitive)', () => {
        expect(isGraphQLRequest('https://api.example.com/GRAPHQL')).toBe(true);
        expect(isGraphQLRequest('https://api.example.com/GraphQL')).toBe(true);
        expect(isGraphQLRequest('https://api.example.com/gRaPhQl')).toBe(true);
      });

      it('detects GraphQL in subpath', () => {
        expect(isGraphQLRequest('https://api.example.com/api/v1/graphql')).toBe(
          true
        );
      });

      it('detects GraphQL with query params', () => {
        expect(
          isGraphQLRequest(
            'https://api.example.com/graphql?operationName=Test&operation=query'
          )
        ).toBe(true);
      });

      it('detects GraphQL in domain', () => {
        expect(isGraphQLRequest('https://graphql.example.com/api')).toBe(true);
      });

      it('detects GraphQL in full path', () => {
        expect(isGraphQLRequest('https://www.fancode.com/graphql')).toBe(true);
      });
    });

    describe('invalid GraphQL URLs', () => {
      it('returns false for non-GraphQL URLs', () => {
        expect(isGraphQLRequest('https://api.example.com/api/users')).toBe(
          false
        );
        expect(isGraphQLRequest('https://api.example.com/rest')).toBe(false);
        expect(isGraphQLRequest('https://api.example.com/graph')).toBe(false);
      });

      it('returns false for empty string', () => {
        expect(isGraphQLRequest('')).toBe(false);
      });

      it('returns false for null', () => {
        expect(isGraphQLRequest(null as any)).toBe(false);
      });

      it('returns false for undefined', () => {
        expect(isGraphQLRequest(undefined as any)).toBe(false);
      });

      it('returns false for non-string types', () => {
        expect(isGraphQLRequest(123 as any)).toBe(false);
        expect(isGraphQLRequest({} as any)).toBe(false);
        expect(isGraphQLRequest([] as any)).toBe(false);
      });
    });
  });

  describe('updateAttributesWithGraphQLData', () => {
    describe('non-GraphQL requests', () => {
      it('returns empty attributes for non-GraphQL URL', () => {
        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/api/users',
          '{"data": "test"}'
        );
        expect(result).toEqual({});
      });

      it('returns empty attributes even with GraphQL-like body', () => {
        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/api',
          '{"operationName": "Test", "operation": "query"}'
        );
        expect(result).toEqual({});
      });
    });

    describe('POST requests - standard payload', () => {
      it('extracts operationName and operationType from body', () => {
        const body = JSON.stringify({
          operationName: 'GetUser',
          operation: 'query',
          query: 'query GetUser { user { id } }',
          variables: {},
        });

        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          body
        );

        expect(result).toEqual({
          'graphql.operation.name': 'GetUser',
          'graphql.operation.type': 'query',
        });
      });

      it('extracts mutation operation', () => {
        const body = JSON.stringify({
          operationName: 'CreateUser',
          operation: 'mutation',
          query: 'mutation CreateUser { createUser { id } }',
        });

        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          body
        );

        expect(result).toEqual({
          'graphql.operation.name': 'CreateUser',
          'graphql.operation.type': 'mutation',
        });
      });

      it('extracts subscription operation', () => {
        const body = JSON.stringify({
          operationName: 'UserSubscription',
          operation: 'subscription',
          query: 'subscription UserSubscription { userUpdated { id } }',
        });

        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          body
        );

        expect(result).toEqual({
          'graphql.operation.name': 'UserSubscription',
          'graphql.operation.type': 'subscription',
        });
      });

      it('handles payload with only operationName - falls back to parse query for operationType', () => {
        const body = JSON.stringify({
          operationName: 'GetUser',
          query: 'query GetUser { user { id } }',
        });

        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          body
        );

        expect(result).toEqual({
          'graphql.operation.name': 'GetUser',
          'graphql.operation.type': 'query', // Extracted from query string
        });
      });

      it('handles payload with only operationType - falls back to parse query for operationName', () => {
        const body = JSON.stringify({
          operation: 'query',
          query: 'query GetUser { user { id } }',
        });

        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          body
        );

        expect(result).toEqual({
          'graphql.operation.name': 'GetUser', // Extracted from query string
          'graphql.operation.type': 'query',
        });
      });
    });

    describe('POST requests - fallback to query parsing', () => {
      it('parses named query from query string when operationName missing', () => {
        const body = JSON.stringify({
          query: 'query GetUser { user { id } }',
          variables: {},
        });

        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          body
        );

        expect(result).toEqual({
          'graphql.operation.name': 'GetUser',
          'graphql.operation.type': 'query',
        });
      });

      it('parses named mutation from query string', () => {
        const body = JSON.stringify({
          query: 'mutation CreateUser { createUser { id } }',
        });

        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          body
        );

        expect(result).toEqual({
          'graphql.operation.name': 'CreateUser',
          'graphql.operation.type': 'mutation',
        });
      });

      it('parses named subscription from query string', () => {
        const body = JSON.stringify({
          query: 'subscription UserUpdated { userUpdated { id } }',
        });

        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          body
        );

        expect(result).toEqual({
          'graphql.operation.name': 'UserUpdated',
          'graphql.operation.type': 'subscription',
        });
      });

      it('parses unnamed query from query string', () => {
        const body = JSON.stringify({
          query: 'query { user { id } }',
        });

        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          body
        );

        expect(result).toEqual({
          'graphql.operation.type': 'query',
        });
      });

      it('parses query with whitespace', () => {
        const body = JSON.stringify({
          query: '   query   MyQuery   {   user { id }   }',
        });

        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          body
        );

        expect(result).toEqual({
          'graphql.operation.name': 'MyQuery',
          'graphql.operation.type': 'query',
        });
      });

      it('prefers payload operationName over parsed query name', () => {
        const body = JSON.stringify({
          operationName: 'CustomName',
          query: 'query GetUser { user { id } }',
        });

        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          body
        );

        expect(result).toEqual({
          'graphql.operation.name': 'CustomName',
          'graphql.operation.type': 'query',
        });
      });

      it('prefers payload operation over parsed query type, but still extracts name from query', () => {
        const body = JSON.stringify({
          operation: 'mutation',
          query: 'query GetUser { user { id } }',
        });

        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          body
        );

        expect(result).toEqual({
          'graphql.operation.name': 'GetUser', // Extracted from query string
          'graphql.operation.type': 'mutation', // From payload (preferred)
        });
      });

      it('merges payload and parsed query data', () => {
        const body = JSON.stringify({
          operationName: 'CustomName',
          query: 'mutation CreateUser { createUser { id } }',
        });

        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          body
        );

        expect(result).toEqual({
          'graphql.operation.name': 'CustomName',
          'graphql.operation.type': 'mutation',
        });
      });
    });

    describe('GET requests - query parameters', () => {
      it('extracts operationName and operationType from query params', () => {
        const url =
          'https://api.example.com/graphql?operationName=GetUser&operation=query';

        const result = updateAttributesWithGraphQLData(url);

        expect(result).toEqual({
          'graphql.operation.name': 'GetUser',
          'graphql.operation.type': 'query',
        });
      });

      it('extracts mutation from query params', () => {
        const url =
          'https://api.example.com/graphql?operationName=CreateUser&operation=mutation';

        const result = updateAttributesWithGraphQLData(url);

        expect(result).toEqual({
          'graphql.operation.name': 'CreateUser',
          'graphql.operation.type': 'mutation',
        });
      });

      it('handles URL-encoded query params', () => {
        const url =
          'https://api.example.com/graphql?operationName=Get%20User&operation=query';

        const result = updateAttributesWithGraphQLData(url);

        expect(result).toEqual({
          'graphql.operation.name': 'Get User',
          'graphql.operation.type': 'query',
        });
      });

      it('handles APQ GET request with extensions', () => {
        const url =
          'https://www.fancode.com/graphql?extensions=%7B%22persistedQuery%22%3A%7B%22version%22%3A1%2C%22sha256Hash%22%3A%22a997e95f29b926ef40e5f0d52438e188b49ea74a43b33f736afb9dc96fd5f99d%22%7D%7D&operation=query&operationName=NudgeSegment';

        const result = updateAttributesWithGraphQLData(url);

        expect(result).toEqual({
          'graphql.operation.name': 'NudgeSegment',
          'graphql.operation.type': 'query',
        });
      });

      it('handles query params with only operationName', () => {
        const url = 'https://api.example.com/graphql?operationName=GetUser';

        const result = updateAttributesWithGraphQLData(url);

        expect(result).toEqual({
          'graphql.operation.name': 'GetUser',
        });
      });

      it('handles query params with only operation', () => {
        const url = 'https://api.example.com/graphql?operation=query';

        const result = updateAttributesWithGraphQLData(url);

        expect(result).toEqual({
          'graphql.operation.type': 'query',
        });
      });
    });

    describe('POST requests - fallback to query params', () => {
      it('falls back to query params when body has no GraphQL data', () => {
        const url =
          'https://api.example.com/graphql?operationName=GetUser&operation=query';
        const body = JSON.stringify({ data: 'not graphql' });

        const result = updateAttributesWithGraphQLData(url, body);

        expect(result).toEqual({
          'graphql.operation.name': 'GetUser',
          'graphql.operation.type': 'query',
        });
      });

      it('prefers body data over query params', () => {
        const url =
          'https://api.example.com/graphql?operationName=OldName&operation=query';
        const body = JSON.stringify({
          operationName: 'NewName',
          operation: 'mutation',
        });

        const result = updateAttributesWithGraphQLData(url, body);

        expect(result).toEqual({
          'graphql.operation.name': 'NewName',
          'graphql.operation.type': 'mutation',
        });
      });
    });

    describe('POST requests - persisted queries (APQ)', () => {
      it('extracts from persisted query payload', () => {
        const body = JSON.stringify({
          operationName: 'GetUser',
          operation: 'query',
          extensions: {
            persistedQuery: {
              version: 1,
              sha256Hash:
                'a997e95f29b926ef40e5f0d52438e188b49ea74a43b33f736afb9dc96fd5f99d',
            },
          },
        });

        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          body
        );

        expect(result).toEqual({
          'graphql.operation.name': 'GetUser',
          'graphql.operation.type': 'query',
        });
      });

      it('handles APQ retry with both query and hash', () => {
        const body = JSON.stringify({
          operationName: 'GetUser',
          operation: 'query',
          query: 'query GetUser { user { id } }',
          extensions: {
            persistedQuery: {
              version: 1,
              sha256Hash: 'hash123',
            },
          },
        });

        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          body
        );

        expect(result).toEqual({
          'graphql.operation.name': 'GetUser',
          'graphql.operation.type': 'query',
        });
      });
    });

    describe('error handling and edge cases', () => {
      it('handles invalid JSON in body', () => {
        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          'invalid json{'
        );

        expect(result).toEqual({});
      });

      it('handles empty body string', () => {
        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          ''
        );

        expect(result).toEqual({});
      });

      it('handles null body', () => {
        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          null
        );

        expect(result).toEqual({});
      });

      it('handles undefined body', () => {
        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          undefined
        );

        expect(result).toEqual({});
      });

      it('handles body with empty object', () => {
        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          '{}'
        );

        expect(result).toEqual({});
      });

      it('handles body with null values', () => {
        const body = JSON.stringify({
          operationName: null,
          operation: null,
        });

        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          body
        );

        expect(result).toEqual({});
      });

      it('handles malformed query string in body', () => {
        const body = JSON.stringify({
          query: 'not a valid graphql query',
        });

        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          body
        );

        expect(result).toEqual({});
      });

      it('handles query string without operation type', () => {
        const body = JSON.stringify({
          query: '{ user { id } }',
        });

        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          body
        );

        expect(result).toEqual({});
      });

      it('handles invalid URL for query params extraction', () => {
        const result = updateAttributesWithGraphQLData('not a url');

        expect(result).toEqual({});
      });

      it('handles URL without query params', () => {
        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql'
        );

        expect(result).toEqual({});
      });
    });

    describe('security and injection prevention', () => {
      it('handles XSS attempts in operationName', () => {
        const body = JSON.stringify({
          operationName: '<script>alert("xss")</script>',
          operation: 'query',
        });

        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          body
        );

        // Should extract as-is (sanitization should happen at span level)
        expect(result['graphql.operation.name']).toBe(
          '<script>alert("xss")</script>'
        );
      });

      it('handles very long operationName', () => {
        const longName = 'A'.repeat(10000);
        const body = JSON.stringify({
          operationName: longName,
          operation: 'query',
        });

        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          body
        );

        expect(result['graphql.operation.name']).toBe(longName);
      });

      it('handles special characters in operationName', () => {
        const body = JSON.stringify({
          operationName: 'Test@#$%^&*()',
          operation: 'query',
        });

        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          body
        );

        expect(result['graphql.operation.name']).toBe('Test@#$%^&*()');
      });

      it('handles deeply nested payload structure', () => {
        const body = JSON.stringify({
          data: {
            nested: {
              operationName: 'GetUser',
              operation: 'query',
            },
          },
        });

        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          body
        );

        // Should not extract from nested structure
        expect(result).toEqual({});
      });
    });

    describe('real-world scenarios', () => {
      it('handles FanCode GraphQL POST request', () => {
        const body = JSON.stringify({
          operationName: 'ClientInfo',
          operation: 'query',
          query:
            'query ClientInfo {\\n  clientInfo {\\n    city\\n    region\\n  }\\n}',
        });

        const result = updateAttributesWithGraphQLData(
          'https://www.fancode.com/graphql',
          body
        );

        expect(result).toEqual({
          'graphql.operation.name': 'ClientInfo',
          'graphql.operation.type': 'query',
        });
      });

      it('handles FanCode GraphQL GET request with APQ', () => {
        const url =
          'https://www.fancode.com/graphql?extensions=%7B%22persistedQuery%22%3A%7B%22version%22%3A1%2C%22sha256Hash%22%3A%22a997e95f29b926ef40e5f0d52438e188b49ea74a43b33f736afb9dc96fd5f99d%22%7D%7D&operation=query&operationName=NudgeSegment&variables=%7B%22input%22%3A%7B%22adIdentity%22%3A%22AD_ROADBLOCK_NATIVE_NUDGE%22%7D%7D';

        const result = updateAttributesWithGraphQLData(url);

        expect(result).toEqual({
          'graphql.operation.name': 'NudgeSegment',
          'graphql.operation.type': 'query',
        });
      });

      it('handles complex query with fragments', () => {
        const body = JSON.stringify({
          operationName: 'userCollectionsV5',
          operation: 'query',
          query:
            'query userCollectionsV5($userPref: UserPreferencesInput!, $countryId: Int!) {\\n  userCollectionsV5(userPref: $userPref, countryId: $countryId) {\\n    collections {\\n      ...CasaFanCollectionPill\\n    }\\n  }\\n}\\nfragment CasaFanCollectionPill on FanCollection {\\n  id\\n  name\\n}',
        });

        const result = updateAttributesWithGraphQLData(
          'https://www.fancode.com/graphql',
          body
        );

        expect(result).toEqual({
          'graphql.operation.name': 'userCollectionsV5',
          'graphql.operation.type': 'query',
        });
      });
    });

    describe('type safety and edge cases', () => {
      it('handles non-string body types gracefully', () => {
        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          {} as any
        );

        expect(result).toEqual({});
      });

      it('handles ArrayBuffer body type', () => {
        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          new ArrayBuffer(10) as any
        );

        expect(result).toEqual({});
      });

      it('handles Blob body type', () => {
        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          new Blob() as any
        );

        expect(result).toEqual({});
      });

      it('handles FormData body type', () => {
        const formData = new FormData();
        const result = updateAttributesWithGraphQLData(
          'https://api.example.com/graphql',
          formData as any
        );

        expect(result).toEqual({});
      });
    });
  });
});
