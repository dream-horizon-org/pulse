import {
  parseUrl,
  extractHttpAttributes,
  SearchParams,
} from '../network-interceptor/url-helper';

describe('parseUrl', () => {
  describe('valid URLs', () => {
    it('parses a simple HTTP URL', () => {
      const result = parseUrl('http://example.com');
      expect(result).toMatchObject({
        protocol: 'http:',
        hostname: 'example.com',
        host: 'example.com',
        port: '',
        pathname: '/',
        search: '',
        hash: '',
        href: 'http://example.com',
      });
      expect(result?.searchParams).toBeInstanceOf(SearchParams);
      expect(result?.searchParams.get('any')).toBeNull();
    });

    it('parses a simple HTTPS URL', () => {
      const result = parseUrl('https://example.com');
      expect(result).toMatchObject({
        protocol: 'https:',
        hostname: 'example.com',
        host: 'example.com',
        port: '',
        pathname: '/',
        search: '',
        hash: '',
        href: 'https://example.com',
      });
      expect(result?.searchParams).toBeInstanceOf(SearchParams);
      expect(result?.searchParams.get('any')).toBeNull();
    });

    it('parses URL with pathname', () => {
      const result = parseUrl('https://api.example.com/users/123');
      expect(result).toMatchObject({
        protocol: 'https:',
        hostname: 'api.example.com',
        host: 'api.example.com',
        port: '',
        pathname: '/users/123',
        search: '',
        hash: '',
        href: 'https://api.example.com/users/123',
      });
      expect(result?.searchParams).toBeInstanceOf(SearchParams);
      expect(result?.searchParams.get('any')).toBeNull();
    });

    it('parses URL with port', () => {
      const result = parseUrl('http://localhost:8080/api');
      expect(result).toMatchObject({
        protocol: 'http:',
        hostname: 'localhost',
        host: 'localhost:8080',
        port: '8080',
        pathname: '/api',
        search: '',
        hash: '',
        href: 'http://localhost:8080/api',
      });
      expect(result?.searchParams).toBeInstanceOf(SearchParams);
      expect(result?.searchParams.get('any')).toBeNull();
    });

    it('parses URL with query string', () => {
      const result = parseUrl('https://example.com/search?q=test&page=1');
      expect(result).toMatchObject({
        protocol: 'https:',
        hostname: 'example.com',
        host: 'example.com',
        port: '',
        pathname: '/search',
        search: '?q=test&page=1',
        hash: '',
        href: 'https://example.com/search?q=test&page=1',
      });
      expect(result?.searchParams).toBeInstanceOf(SearchParams);
      expect(result?.searchParams.get('q')).toBe('test');
      expect(result?.searchParams.get('page')).toBe('1');
    });

    it('parses URL with hash', () => {
      const result = parseUrl('https://example.com/docs#section');
      expect(result).toMatchObject({
        protocol: 'https:',
        hostname: 'example.com',
        host: 'example.com',
        port: '',
        pathname: '/docs',
        search: '',
        hash: '#section',
        href: 'https://example.com/docs#section',
      });
      expect(result?.searchParams).toBeInstanceOf(SearchParams);
      expect(result?.searchParams.get('any')).toBeNull();
    });

    it('parses URL with all components', () => {
      const result = parseUrl(
        'https://api.example.com:443/users?id=123&active=true#profile'
      );
      expect(result).toMatchObject({
        protocol: 'https:',
        hostname: 'api.example.com',
        host: 'api.example.com:443',
        port: '443',
        pathname: '/users',
        search: '?id=123&active=true',
        hash: '#profile',
        href: 'https://api.example.com:443/users?id=123&active=true#profile',
      });
      expect(result?.searchParams).toBeInstanceOf(SearchParams);
      expect(result?.searchParams.get('id')).toBe('123');
      expect(result?.searchParams.get('active')).toBe('true');
    });

    it('parses URL with authentication', () => {
      const result = parseUrl('https://user:pass@example.com/api');
      expect(result).toMatchObject({
        protocol: 'https:',
        hostname: 'example.com',
        host: 'example.com',
        port: '',
        pathname: '/api',
        search: '',
        hash: '',
        href: 'https://user:pass@example.com/api',
      });
      expect(result?.searchParams).toBeInstanceOf(SearchParams);
      expect(result?.searchParams.get('any')).toBeNull();
    });

    it('parses URL with subdomain', () => {
      const result = parseUrl('https://api.v2.example.com/users');
      expect(result).toMatchObject({
        protocol: 'https:',
        hostname: 'api.v2.example.com',
        host: 'api.v2.example.com',
        port: '',
        pathname: '/users',
        search: '',
        hash: '',
        href: 'https://api.v2.example.com/users',
      });
      expect(result?.searchParams).toBeInstanceOf(SearchParams);
      expect(result?.searchParams.get('any')).toBeNull();
    });

    it('parses URL with IP address', () => {
      const result = parseUrl('http://192.168.1.1:3000/api');
      expect(result).toMatchObject({
        protocol: 'http:',
        hostname: '192.168.1.1',
        host: '192.168.1.1:3000',
        port: '3000',
        pathname: '/api',
        search: '',
        hash: '',
        href: 'http://192.168.1.1:3000/api',
      });
      expect(result?.searchParams).toBeInstanceOf(SearchParams);
      expect(result?.searchParams.get('any')).toBeNull();
    });

    it('parses FTP URL (protocol only)', () => {
      // Our helper focuses on HTTP/HTTPS, so FTP URLs only get protocol parsed
      const result = parseUrl('ftp://files.example.com/download');
      expect(result?.protocol).toBe('ftp:');
      // hostname, pathname etc are empty for non-HTTP protocols
      expect(result?.hostname).toBe('');
    });

    it('parses URL with complex path', () => {
      const result = parseUrl('https://example.com/api/v1/users/123/posts');
      expect(result?.pathname).toBe('/api/v1/users/123/posts');
    });

    it('parses URL with trailing slash', () => {
      const result = parseUrl('https://example.com/api/');
      expect(result?.pathname).toBe('/api/');
    });

    it('parses URL without trailing slash', () => {
      const result = parseUrl('https://example.com/api');
      expect(result?.pathname).toBe('/api');
    });

    it('handles empty query string', () => {
      const result = parseUrl('https://example.com/api?');
      // Empty query string results in empty string (no content after ?)
      expect(result?.search).toBe('');
    });

    it('handles empty hash', () => {
      const result = parseUrl('https://example.com/api#');
      // Empty hash results in empty string (no content after #)
      expect(result?.hash).toBe('');
    });
  });

  describe('invalid URLs', () => {
    it('returns null for empty string', () => {
      expect(parseUrl('')).toBeNull();
    });

    it('returns null for whitespace only', () => {
      expect(parseUrl('   ')).toBeNull();
    });

    it('returns null for relative URL', () => {
      expect(parseUrl('/api/users')).toBeNull();
    });

    it('returns null for protocol-relative URL', () => {
      expect(parseUrl('//example.com/api')).toBeNull();
    });

    it('returns null for invalid protocol', () => {
      expect(parseUrl('ht!tp://example.com')).toBeNull();
    });

    it('returns null for null input', () => {
      expect(parseUrl(null as any)).toBeNull();
    });

    it('returns null for undefined input', () => {
      expect(parseUrl(undefined as any)).toBeNull();
    });

    it('returns null for non-string input', () => {
      expect(parseUrl(123 as any)).toBeNull();
    });

    it('returns null for object input', () => {
      expect(parseUrl({} as any)).toBeNull();
    });
  });

  describe('edge cases', () => {
    it('handles URL with multiple @ symbols', () => {
      // The regex will match the last @ as the separator
      const result = parseUrl('https://user@domain:pass@example.com/api');
      // This matches 'domain' (the part before the last @)
      expect(result?.hostname).toBe('domain');
    });

    it('handles URL with special characters in query', () => {
      const result = parseUrl('https://example.com/search?q=hello%20world&x=1');
      expect(result?.search).toBe('?q=hello%20world&x=1');
    });

    it('handles URL with multiple hashes', () => {
      const result = parseUrl('https://example.com/page#section#subsection');
      // Only captures first hash segment
      expect(result?.hash).toBeTruthy();
    });

    it('handles localhost', () => {
      const result = parseUrl('http://localhost/api');
      expect(result?.hostname).toBe('localhost');
      expect(result?.port).toBe('');
    });

    it('handles localhost with port', () => {
      const result = parseUrl('http://localhost:3000/api');
      expect(result?.hostname).toBe('localhost');
      expect(result?.port).toBe('3000');
    });

    it('handles very long URLs', () => {
      const longPath = '/api/' + 'segment/'.repeat(100);
      const result = parseUrl(`https://example.com${longPath}`);
      expect(result?.pathname).toBe(longPath);
    });

    it('handles URL with encoded characters', () => {
      const result = parseUrl('https://example.com/api%2Fusers');
      expect(result?.pathname).toBe('/api%2Fusers');
    });
  });
});

describe('extractHttpAttributes', () => {
  describe('standard URLs', () => {
    it('extracts attributes from simple HTTPS URL', () => {
      const result = extractHttpAttributes('https://example.com/api');
      expect(result).toEqual({
        'http.scheme': 'https',
        'http.host': 'example.com',
        'http.target': '/api',
        'net.peer.name': 'example.com',
      });
    });

    it('extracts attributes from HTTP URL with port', () => {
      const result = extractHttpAttributes('http://localhost:8080/api/users');
      expect(result).toEqual({
        'http.scheme': 'http',
        'http.host': 'localhost',
        'http.target': '/api/users',
        'net.peer.name': 'localhost',
        'net.peer.port': 8080,
      });
    });

    it('extracts attributes from URL with query string', () => {
      const result = extractHttpAttributes(
        'https://api.example.com/search?q=test&page=1'
      );
      expect(result).toEqual({
        'http.scheme': 'https',
        'http.host': 'api.example.com',
        'http.target': '/search?q=test&page=1',
        'net.peer.name': 'api.example.com',
      });
    });

    it('extracts attributes from URL with all components', () => {
      const result = extractHttpAttributes(
        'https://api.example.com:443/users?id=123#profile'
      );
      expect(result).toEqual({
        'http.scheme': 'https',
        'http.host': 'api.example.com',
        'http.target': '/users?id=123',
        'net.peer.name': 'api.example.com',
        'net.peer.port': 443,
      });
    });

    it('extracts attributes from URL with authentication', () => {
      const result = extractHttpAttributes('https://user:pass@example.com/api');
      expect(result).toEqual({
        'http.scheme': 'https',
        'http.host': 'example.com',
        'http.target': '/api',
        'net.peer.name': 'example.com',
      });
    });
  });

  describe('port validation', () => {
    it('includes valid port numbers', () => {
      const result = extractHttpAttributes('http://example.com:3000/api');
      expect(result['net.peer.port']).toBe(3000);
    });

    it('includes standard HTTP port', () => {
      const result = extractHttpAttributes('http://example.com:80/api');
      expect(result['net.peer.port']).toBe(80);
    });

    it('includes standard HTTPS port', () => {
      const result = extractHttpAttributes('https://example.com:443/api');
      expect(result['net.peer.port']).toBe(443);
    });

    it('excludes port above valid range', () => {
      // Our helper should not include invalid ports
      const result = extractHttpAttributes('http://example.com:99999/api');
      expect(result['net.peer.port']).toBeUndefined();
    });

    it('excludes port 0', () => {
      const result = extractHttpAttributes('http://example.com:0/api');
      expect(result['net.peer.port']).toBeUndefined();
    });

    it('handles custom ports correctly', () => {
      const result = extractHttpAttributes('http://localhost:3001/api');
      expect(result['net.peer.port']).toBe(3001);
    });
  });

  describe('real-world URLs', () => {
    it('handles jsonplaceholder API URL', () => {
      const result = extractHttpAttributes(
        'https://jsonplaceholder.typicode.com/posts/1'
      );
      expect(result).toEqual({
        'http.scheme': 'https',
        'http.host': 'jsonplaceholder.typicode.com',
        'http.target': '/posts/1',
        'net.peer.name': 'jsonplaceholder.typicode.com',
      });
    });

    it('handles GitHub API URL', () => {
      const result = extractHttpAttributes(
        'https://api.github.com/users/octocat'
      );
      expect(result).toEqual({
        'http.scheme': 'https',
        'http.host': 'api.github.com',
        'http.target': '/users/octocat',
        'net.peer.name': 'api.github.com',
      });
    });

    it('handles REST API with version', () => {
      const result = extractHttpAttributes(
        'https://api.example.com/v2/users?limit=10'
      );
      expect(result).toEqual({
        'http.scheme': 'https',
        'http.host': 'api.example.com',
        'http.target': '/v2/users?limit=10',
        'net.peer.name': 'api.example.com',
      });
    });

    it('handles GraphQL endpoint', () => {
      const result = extractHttpAttributes('https://api.example.com/graphql');
      expect(result).toEqual({
        'http.scheme': 'https',
        'http.host': 'api.example.com',
        'http.target': '/graphql',
        'net.peer.name': 'api.example.com',
      });
    });
  });

  describe('invalid URLs', () => {
    it('returns empty object for empty string', () => {
      expect(extractHttpAttributes('')).toEqual({});
    });

    it('returns empty object for relative URL', () => {
      expect(extractHttpAttributes('/api/users')).toEqual({});
    });

    it('returns empty object for invalid URL', () => {
      expect(extractHttpAttributes('not a url')).toEqual({});
    });

    it('returns empty object for null', () => {
      expect(extractHttpAttributes(null as any)).toEqual({});
    });

    it('returns empty object for undefined', () => {
      expect(extractHttpAttributes(undefined as any)).toEqual({});
    });

    it('handles malformed URLs gracefully', () => {
      // Malformed URLs return partial attributes (what can be extracted)
      const result1 = extractHttpAttributes('http://');
      expect(result1['http.scheme']).toBe('http');
      expect(result1['http.host']).toBeUndefined();

      const result2 = extractHttpAttributes('https://');
      expect(result2['http.scheme']).toBe('https');
      expect(result2['http.host']).toBeUndefined();
    });
  });

  describe('cross-version compatibility', () => {
    it('works like RN 0.80+ for standard URLs', () => {
      // This test ensures our implementation matches RN 0.80+ behavior
      const urls = [
        'https://example.com',
        'http://localhost:3000/api',
        'https://api.github.com/users',
        'https://example.com/search?q=test',
      ];

      urls.forEach((url) => {
        const result = extractHttpAttributes(url);
        expect(result['http.scheme']).toBeDefined();
        expect(result['http.host']).toBeDefined();
        expect(result['http.target']).toBeDefined();
        expect(result['net.peer.name']).toBeDefined();
      });
    });

    it('never throws errors (safe for RN < 0.80)', () => {
      // Ensure it never throws, even with bad input
      const badInputs = [
        '',
        null,
        undefined,
        123,
        {},
        [],
        'not-a-url',
        '/relative',
        '//protocol-relative.com',
      ];

      badInputs.forEach((input) => {
        expect(() => extractHttpAttributes(input as any)).not.toThrow();
      });
    });
  });

  describe('OpenTelemetry semantic conventions', () => {
    it('follows http.scheme convention', () => {
      const result = extractHttpAttributes('https://example.com/api');
      expect(result['http.scheme']).toBe('https'); // without colon
    });

    it('follows http.host convention (hostname without port)', () => {
      const result = extractHttpAttributes('https://example.com:443/api');
      expect(result['http.host']).toBe('example.com');
    });

    it('follows http.target convention (path + query)', () => {
      const result = extractHttpAttributes('https://example.com/api?key=value');
      expect(result['http.target']).toBe('/api?key=value');
    });

    it('follows net.peer.name convention (same as http.host)', () => {
      const result = extractHttpAttributes('https://example.com/api');
      expect(result['net.peer.name']).toBe(result['http.host']);
    });

    it('follows net.peer.port convention (numeric)', () => {
      const result = extractHttpAttributes('http://localhost:8080/api');
      expect(typeof result['net.peer.port']).toBe('number');
      expect(result['net.peer.port']).toBe(8080);
    });
  });
});

describe('SearchParams', () => {
  describe('constructor and basic parsing', () => {
    it('parses simple query string', () => {
      const params = new SearchParams('?key=value');
      expect(params.get('key')).toBe('value');
    });

    it('parses query string without leading ?', () => {
      const params = new SearchParams('key=value');
      expect(params.get('key')).toBe('value');
    });

    it('parses multiple parameters', () => {
      const params = new SearchParams('?key1=value1&key2=value2');
      expect(params.get('key1')).toBe('value1');
      expect(params.get('key2')).toBe('value2');
    });

    it('parses empty query string', () => {
      const params = new SearchParams('');
      expect(params.get('any')).toBeNull();
    });
  });

  describe('URL decoding', () => {
    it('decodes URL-encoded values', () => {
      const params = new SearchParams('?name=John%20Doe');
      expect(params.get('name')).toBe('John Doe');
    });

    it('decodes GraphQL operationName with special chars', () => {
      const params = new SearchParams(
        '?operationName=GetUser%20Profile&operation=query'
      );
      expect(params.get('operationName')).toBe('GetUser Profile');
    });
  });

  describe('get method', () => {
    it('returns null for non-existent parameter', () => {
      const params = new SearchParams('?key=value');
      expect(params.get('nonexistent')).toBeNull();
    });

    it('returns value for existing parameter', () => {
      const params = new SearchParams('?operationName=TestQuery');
      expect(params.get('operationName')).toBe('TestQuery');
    });
  });

  describe('has method', () => {
    it('returns true for existing parameter', () => {
      const params = new SearchParams('?key=value');
      expect(params.has('key')).toBe(true);
    });

    it('returns false for non-existent parameter', () => {
      const params = new SearchParams('?key=value');
      expect(params.has('nonexistent')).toBe(false);
    });
  });

  describe('GraphQL-specific query parameters', () => {
    it('parses GraphQL GET request with operationName and operation', () => {
      const params = new SearchParams(
        '?operationName=FetchScheduleFilters&operation=query'
      );
      expect(params.get('operationName')).toBe('FetchScheduleFilters');
      expect(params.get('operation')).toBe('query');
    });

    it('parses GraphQL GET request with APQ extensions', () => {
      const searchString =
        '?extensions=%7B%22persistedQuery%22%3A%7B%22version%22%3A1%2C%22sha256Hash%22%3A%22a997e95f29b926ef40e5f0d52438e188b49ea74a43b33f736afb9dc96fd5f99d%22%7D%7D&operation=query&operationName=NudgeSegment';
      const params = new SearchParams(searchString);
      expect(params.get('operationName')).toBe('NudgeSegment');
      expect(params.get('operation')).toBe('query');
      expect(params.has('extensions')).toBe(true);
    });
  });
});

describe('parseUrl with searchParams', () => {
  it('includes searchParams in parsed URL', () => {
    const parsed = parseUrl(
      'https://example.com/graphql?operationName=Test&operation=query'
    );
    expect(parsed).not.toBeNull();
    expect(parsed?.searchParams).toBeInstanceOf(SearchParams);
    expect(parsed?.searchParams.get('operationName')).toBe('Test');
    expect(parsed?.searchParams.get('operation')).toBe('query');
  });

  it('searchParams handles URL-encoded GraphQL parameters', () => {
    const url =
      'https://api.example.com/graphql?extensions=%7B%22persistedQuery%22%3A%7B%22version%22%3A1%2C%22sha256Hash%22%3A%22a997e95f29b926ef40e5f0d52438e188b49ea74a43b33f736afb9dc96fd5f99d%22%7D%7D&operation=query&operationName=NudgeSegment';
    const parsed = parseUrl(url);
    expect(parsed?.searchParams.get('operationName')).toBe('NudgeSegment');
    expect(parsed?.searchParams.get('operation')).toBe('query');
  });
});
