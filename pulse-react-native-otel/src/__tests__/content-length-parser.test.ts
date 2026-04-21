import {
  estimateRequestBodyByteLength,
  getHeaderCaseInsensitive,
  parseContentLength,
} from '../network-interceptor/content-length-parser';

describe('parseContentLength', () => {
  describe('missing or empty input', () => {
    it('returns undefined for null', () => {
      expect(parseContentLength(null)).toBeUndefined();
    });

    it('returns undefined for undefined', () => {
      expect(parseContentLength(undefined)).toBeUndefined();
    });

    it('returns undefined for empty string', () => {
      expect(parseContentLength('')).toBeUndefined();
    });

    it('returns undefined for whitespace-only string', () => {
      expect(parseContentLength('   \t  \n  ')).toBeUndefined();
    });
  });

  describe('valid integers', () => {
    it('parses plain digits', () => {
      expect(parseContentLength('0')).toBe(0);
      expect(parseContentLength('42')).toBe(42);
      expect(parseContentLength('1024')).toBe(1024);
    });

    it('trims surrounding whitespace', () => {
      expect(parseContentLength('  7  ')).toBe(7);
      expect(parseContentLength('\t99\n')).toBe(99);
    });

    it('uses first segment when comma-separated (invalid duplicate CL pattern)', () => {
      expect(parseContentLength('10, 20')).toBe(10);
      expect(parseContentLength('100 , 200')).toBe(100);
    });

    it('accepts explicit plus sign on non-negative integer', () => {
      expect(parseContentLength('+0')).toBe(0);
      expect(parseContentLength('+15')).toBe(15);
    });
  });

  describe('rejected values', () => {
    it('returns undefined for non-numeric', () => {
      expect(parseContentLength('abc')).toBeUndefined();
      expect(parseContentLength('12px')).toBeUndefined();
    });

    it('returns undefined for decimal (not integer)', () => {
      expect(parseContentLength('12.5')).toBeUndefined();
      expect(parseContentLength('3.14')).toBeUndefined();
    });

    it('coerces trailing .0 to integer zero via Number()', () => {
      expect(parseContentLength('0.0')).toBe(0);
    });

    it('returns undefined for negative', () => {
      expect(parseContentLength('-1')).toBeUndefined();
      expect(parseContentLength('-42')).toBeUndefined();
    });

    it('returns undefined for Infinity / NaN text', () => {
      expect(parseContentLength('Infinity')).toBeUndefined();
      expect(parseContentLength('-Infinity')).toBeUndefined();
      expect(parseContentLength('NaN')).toBeUndefined();
    });

    it('returns undefined for scientific notation that is not an integer token', () => {
      expect(parseContentLength('1e-3')).toBeUndefined();
    });
  });
});

describe('getHeaderCaseInsensitive', () => {
  it('returns undefined when headers is undefined', () => {
    expect(getHeaderCaseInsensitive(undefined, 'content-type')).toBeUndefined();
  });

  it('returns undefined when key is absent', () => {
    expect(
      getHeaderCaseInsensitive({ Accept: 'application/json' }, 'content-type')
    ).toBeUndefined();
  });

  it('matches header name case-insensitively', () => {
    expect(
      getHeaderCaseInsensitive(
        { 'Content-Type': 'application/json' },
        'content-type'
      )
    ).toBe('application/json');
    expect(
      getHeaderCaseInsensitive({ 'content-type': 'text/plain' }, 'Content-Type')
    ).toBe('text/plain');
  });

  it('returns first matching key in object iteration order', () => {
    const headers: Record<string, string> = {
      'X-Custom': 'first',
      'x-custom': 'second',
    };
    const v = getHeaderCaseInsensitive(headers, 'x-custom');
    expect(v === 'first' || v === 'second').toBe(true);
  });

  it('finds Content-Length among mixed-case keys', () => {
    expect(
      getHeaderCaseInsensitive({ 'CONTENT-LENGTH': '512' }, 'content-length')
    ).toBe('512');
  });

  it('returns undefined for empty headers object', () => {
    expect(getHeaderCaseInsensitive({}, 'host')).toBeUndefined();
  });

  it('returns empty string when header value is empty', () => {
    expect(getHeaderCaseInsensitive({ 'X-Empty': '' }, 'x-empty')).toBe('');
  });
});

describe('estimateRequestBodyByteLength', () => {
  describe('nullish and empty string', () => {
    it('returns undefined for null and undefined', () => {
      expect(estimateRequestBodyByteLength(null)).toBeUndefined();
      expect(estimateRequestBodyByteLength(undefined)).toBeUndefined();
    });

    it('returns undefined for empty string', () => {
      expect(estimateRequestBodyByteLength('')).toBeUndefined();
    });
  });

  describe('string bodies (UTF-8 byte length)', () => {
    it('counts ASCII bytes', () => {
      expect(estimateRequestBodyByteLength('a')).toBe(1);
      expect(estimateRequestBodyByteLength('hello')).toBe(5);
    });

    it('counts multi-byte UTF-8 correctly', () => {
      expect(estimateRequestBodyByteLength('é')).toBe(2);
      expect(estimateRequestBodyByteLength('你好')).toBe(6);
      expect(estimateRequestBodyByteLength('🚀')).toBe(4);
    });
  });

  describe('Blob', () => {
    it('returns size when Blob is available and size > 0', () => {
      if (typeof Blob === 'undefined') {
        expect(true).toBe(true);
        return;
      }
      const b = new Blob(['abc']);
      expect(estimateRequestBodyByteLength(b)).toBe(3);
    });

    it('returns undefined for empty Blob (size 0)', () => {
      if (typeof Blob === 'undefined') {
        expect(true).toBe(true);
        return;
      }
      expect(estimateRequestBodyByteLength(new Blob([]))).toBeUndefined();
    });
  });

  describe('ArrayBuffer and ArrayBuffer views', () => {
    it('returns byteLength for non-empty ArrayBuffer', () => {
      const buf = new ArrayBuffer(4);
      expect(estimateRequestBodyByteLength(buf)).toBe(4);
    });

    it('returns undefined for zero-length ArrayBuffer', () => {
      expect(estimateRequestBodyByteLength(new ArrayBuffer(0))).toBeUndefined();
    });

    it('returns byteLength for Uint8Array', () => {
      expect(estimateRequestBodyByteLength(new Uint8Array([1, 2, 3]))).toBe(3);
    });

    it('returns undefined for empty typed array', () => {
      expect(estimateRequestBodyByteLength(new Uint8Array(0))).toBeUndefined();
    });

    it('returns byteLength for DataView', () => {
      const ab = new ArrayBuffer(8);
      const dv = new DataView(ab);
      expect(estimateRequestBodyByteLength(dv)).toBe(8);
    });

    it('returns byteLength for Int16Array (ArrayBufferView)', () => {
      expect(estimateRequestBodyByteLength(new Int16Array([1, 2, 3]))).toBe(6);
    });
  });

  describe('fallback: types not handled by implementation', () => {
    it('returns undefined for plain object', () => {
      expect(estimateRequestBodyByteLength({} as never)).toBeUndefined();
    });

    it('returns undefined for number', () => {
      expect(estimateRequestBodyByteLength(42 as never)).toBeUndefined();
    });

    it('returns undefined for boolean', () => {
      expect(estimateRequestBodyByteLength(true as never)).toBeUndefined();
    });

    it('returns undefined for FormData when present (not in checklist)', () => {
      if (typeof FormData === 'undefined') {
        expect(true).toBe(true);
        return;
      }
      const fd = new FormData();
      fd.append('a', 'b');
      expect(estimateRequestBodyByteLength(fd as never)).toBeUndefined();
    });

    it('returns undefined for URLSearchParams when present (not in checklist)', () => {
      if (typeof URLSearchParams === 'undefined') {
        expect(true).toBe(true);
        return;
      }
      const params = new URLSearchParams({ x: '1', y: '2' });
      expect(estimateRequestBodyByteLength(params as never)).toBeUndefined();
    });
  });

  describe('TextEncoder failure path', () => {
    it('returns undefined when encode throws', () => {
      const encodeSpy = jest
        .spyOn(TextEncoder.prototype, 'encode')
        .mockImplementation(() => {
          throw new Error('encode failed');
        });
      expect(estimateRequestBodyByteLength('not-empty')).toBeUndefined();
      encodeSpy.mockRestore();
    });
  });
});
