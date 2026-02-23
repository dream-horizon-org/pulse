import {
  normalizeHeaderName,
  shouldCaptureHeader,
} from '../network-interceptor/header-helper';

describe('normalizeHeaderName', () => {
  describe('basic normalization', () => {
    it('converts header name to lowercase', () => {
      expect(normalizeHeaderName('Content-Type')).toBe('content_type');
      expect(normalizeHeaderName('CONTENT-TYPE')).toBe('content_type');
      expect(normalizeHeaderName('Content-Type')).toBe('content_type');
    });

    it('replaces dashes with underscores', () => {
      expect(normalizeHeaderName('Content-Type')).toBe('content_type');
      expect(normalizeHeaderName('X-Request-ID')).toBe('x_request_id');
      expect(normalizeHeaderName('X-Custom-Header')).toBe('x_custom_header');
    });

    it('handles headers with multiple dashes', () => {
      expect(normalizeHeaderName('X-Request-ID-Value')).toBe(
        'x_request_id_value'
      );
      expect(normalizeHeaderName('X-Custom-Header-Name')).toBe(
        'x_custom_header_name'
      );
    });
  });

  describe('edge cases', () => {
    it('handles already lowercase headers', () => {
      expect(normalizeHeaderName('content-type')).toBe('content_type');
      expect(normalizeHeaderName('x-request-id')).toBe('x_request_id');
    });

    it('handles headers without dashes', () => {
      expect(normalizeHeaderName('Authorization')).toBe('authorization');
      expect(normalizeHeaderName('UserAgent')).toBe('useragent');
    });

    it('handles single character headers', () => {
      expect(normalizeHeaderName('X')).toBe('x');
      expect(normalizeHeaderName('A')).toBe('a');
    });

    it('handles empty string', () => {
      expect(normalizeHeaderName('')).toBe('');
    });

    it('handles headers with only dashes', () => {
      expect(normalizeHeaderName('---')).toBe('___');
      expect(normalizeHeaderName('-')).toBe('_');
    });

    it('handles headers with underscores (preserves them)', () => {
      expect(normalizeHeaderName('X_Custom_Header')).toBe('x_custom_header');
      expect(normalizeHeaderName('Content_Type')).toBe('content_type');
    });

    it('handles mixed dashes and underscores', () => {
      expect(normalizeHeaderName('X-Custom_Header-Name')).toBe(
        'x_custom_header_name'
      );
    });
  });

  describe('real-world header examples', () => {
    it('normalizes common HTTP headers', () => {
      expect(normalizeHeaderName('Content-Type')).toBe('content_type');
      expect(normalizeHeaderName('Content-Length')).toBe('content_length');
      expect(normalizeHeaderName('Accept-Encoding')).toBe('accept_encoding');
      expect(normalizeHeaderName('User-Agent')).toBe('user_agent');
      expect(normalizeHeaderName('Authorization')).toBe('authorization');
      expect(normalizeHeaderName('X-Request-ID')).toBe('x_request_id');
      expect(normalizeHeaderName('X-Forwarded-For')).toBe('x_forwarded_for');
      expect(normalizeHeaderName('X-Custom-Header')).toBe('x_custom_header');
    });

    it('normalizes custom headers', () => {
      expect(normalizeHeaderName('X-Pulse-RN-Tracked')).toBe(
        'x_pulse_rn_tracked'
      );
      expect(normalizeHeaderName('X-API-Key')).toBe('x_api_key');
      expect(normalizeHeaderName('X-Client-Version')).toBe('x_client_version');
    });
  });
});

describe('shouldCaptureHeader', () => {
  describe('basic functionality', () => {
    it('returns true when header is in the list (exact match)', () => {
      const headerList = ['Content-Type', 'Authorization', 'X-Request-ID'];
      expect(shouldCaptureHeader('Content-Type', headerList)).toBe(true);
      expect(shouldCaptureHeader('Authorization', headerList)).toBe(true);
      expect(shouldCaptureHeader('X-Request-ID', headerList)).toBe(true);
    });

    it('returns false when header is not in the list', () => {
      const headerList = ['Content-Type', 'Authorization'];
      expect(shouldCaptureHeader('X-Request-ID', headerList)).toBe(false);
      expect(shouldCaptureHeader('User-Agent', headerList)).toBe(false);
    });

    it('returns false for empty header list', () => {
      expect(shouldCaptureHeader('Content-Type', [])).toBe(false);
      expect(shouldCaptureHeader('Any-Header', [])).toBe(false);
    });
  });

  describe('case-insensitive matching', () => {
    it('matches headers regardless of case', () => {
      const headerList = ['Content-Type', 'Authorization'];
      expect(shouldCaptureHeader('content-type', headerList)).toBe(true);
      expect(shouldCaptureHeader('CONTENT-TYPE', headerList)).toBe(true);
      expect(shouldCaptureHeader('Content-Type', headerList)).toBe(true);
      expect(shouldCaptureHeader('CoNtEnT-TyPe', headerList)).toBe(true);
    });

    it('matches config headers regardless of case', () => {
      const headerList = ['content-type', 'AUTHORIZATION'];
      expect(shouldCaptureHeader('Content-Type', headerList)).toBe(true);
      expect(shouldCaptureHeader('authorization', headerList)).toBe(true);
      expect(shouldCaptureHeader('AUTHORIZATION', headerList)).toBe(true);
    });

    it('handles mixed case in both config and header name', () => {
      const headerList = ['Content-Type', 'X-Request-ID'];
      expect(shouldCaptureHeader('content-type', headerList)).toBe(true);
      expect(shouldCaptureHeader('x-request-id', headerList)).toBe(true);
      expect(shouldCaptureHeader('X-REQUEST-ID', headerList)).toBe(true);
    });
  });

  describe('edge cases', () => {
    it('handles empty header name', () => {
      const headerList = ['Content-Type', 'Authorization'];
      expect(shouldCaptureHeader('', headerList)).toBe(false);
    });

    it('handles empty string in header list', () => {
      const headerList = ['', 'Content-Type'];
      expect(shouldCaptureHeader('', headerList)).toBe(true);
      expect(shouldCaptureHeader('Content-Type', headerList)).toBe(true);
    });

    it('handles duplicate headers in list', () => {
      const headerList = ['Content-Type', 'Content-Type', 'Authorization'];
      expect(shouldCaptureHeader('Content-Type', headerList)).toBe(true);
    });

    it('handles single header in list', () => {
      const headerList = ['Content-Type'];
      expect(shouldCaptureHeader('Content-Type', headerList)).toBe(true);
      expect(shouldCaptureHeader('Authorization', headerList)).toBe(false);
    });

    it('handles very long header names', () => {
      const longHeader = 'X-' + 'A'.repeat(1000) + '-Header';
      const headerList = [longHeader, 'Content-Type'];
      expect(shouldCaptureHeader(longHeader, headerList)).toBe(true);
      expect(shouldCaptureHeader('Content-Type', headerList)).toBe(true);
    });
  });

  describe('real-world scenarios', () => {
    it('matches common request headers', () => {
      const requestHeaders = [
        'Content-Type',
        'Authorization',
        'X-Request-ID',
        'User-Agent',
      ];
      expect(shouldCaptureHeader('Content-Type', requestHeaders)).toBe(true);
      expect(shouldCaptureHeader('Authorization', requestHeaders)).toBe(true);
      expect(shouldCaptureHeader('X-Request-ID', requestHeaders)).toBe(true);
      expect(shouldCaptureHeader('User-Agent', requestHeaders)).toBe(true);
      expect(shouldCaptureHeader('Accept', requestHeaders)).toBe(false);
    });

    it('matches common response headers', () => {
      const responseHeaders = [
        'Content-Type',
        'Content-Length',
        'X-Request-ID',
        'Set-Cookie',
      ];
      expect(shouldCaptureHeader('Content-Type', responseHeaders)).toBe(true);
      expect(shouldCaptureHeader('Content-Length', responseHeaders)).toBe(true);
      expect(shouldCaptureHeader('X-Request-ID', responseHeaders)).toBe(true);
      expect(shouldCaptureHeader('Set-Cookie', responseHeaders)).toBe(true);
      expect(shouldCaptureHeader('Cache-Control', responseHeaders)).toBe(false);
    });

    it('handles custom headers', () => {
      const customHeaders = ['X-API-Key', 'X-Client-Version', 'X-Pulse-ID'];
      expect(shouldCaptureHeader('X-API-Key', customHeaders)).toBe(true);
      expect(shouldCaptureHeader('X-Client-Version', customHeaders)).toBe(true);
      expect(shouldCaptureHeader('X-Pulse-ID', customHeaders)).toBe(true);
      expect(shouldCaptureHeader('X-Other-Header', customHeaders)).toBe(false);
    });
  });

  describe('OpenTelemetry semantic conventions compatibility', () => {
    it('works with normalized header names from OpenTelemetry spec', () => {
      const headerList = ['Content-Type', 'X-Request-ID'];
      expect(shouldCaptureHeader('Content-Type', headerList)).toBe(true);
      expect(shouldCaptureHeader('X-Request-ID', headerList)).toBe(true);
    });
  });
});
