import {
  estimateHttpBodyByteLength,
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

describe('estimateHttpBodyByteLength', () => {
  describe('nullish and empty string', () => {
    it('returns undefined for null and undefined', () => {
      expect(estimateHttpBodyByteLength(null)).toBeUndefined();
      expect(estimateHttpBodyByteLength(undefined)).toBeUndefined();
    });

    it('returns undefined for empty string', () => {
      expect(estimateHttpBodyByteLength('')).toBeUndefined();
    });
  });

  describe('string bodies (UTF-8 byte length)', () => {
    it('counts ASCII bytes', () => {
      expect(estimateHttpBodyByteLength('a')).toBe(1);
      expect(estimateHttpBodyByteLength('hello')).toBe(5);
    });

    it('counts multi-byte UTF-8 correctly', () => {
      expect(estimateHttpBodyByteLength('é')).toBe(2);
      expect(estimateHttpBodyByteLength('你好')).toBe(6);
      expect(estimateHttpBodyByteLength('🚀')).toBe(4);
    });
  });

  describe('Blob', () => {
    it('returns size when Blob is available and size > 0', () => {
      if (typeof Blob === 'undefined') {
        expect(true).toBe(true);
        return;
      }
      const b = new Blob(['abc']);
      expect(estimateHttpBodyByteLength(b)).toBe(3);
    });

    it('returns undefined for empty Blob (size 0)', () => {
      if (typeof Blob === 'undefined') {
        expect(true).toBe(true);
        return;
      }
      expect(estimateHttpBodyByteLength(new Blob([]))).toBeUndefined();
    });
  });

  describe('ArrayBuffer and ArrayBuffer views', () => {
    it('returns byteLength for non-empty ArrayBuffer', () => {
      const buf = new ArrayBuffer(4);
      expect(estimateHttpBodyByteLength(buf)).toBe(4);
    });

    it('returns undefined for zero-length ArrayBuffer', () => {
      expect(estimateHttpBodyByteLength(new ArrayBuffer(0))).toBeUndefined();
    });

    it('returns byteLength for Uint8Array', () => {
      expect(estimateHttpBodyByteLength(new Uint8Array([1, 2, 3]))).toBe(3);
    });

    it('returns undefined for empty typed array', () => {
      expect(estimateHttpBodyByteLength(new Uint8Array(0))).toBeUndefined();
    });

    it('returns byteLength for DataView', () => {
      const ab = new ArrayBuffer(8);
      const dv = new DataView(ab);
      expect(estimateHttpBodyByteLength(dv)).toBe(8);
    });

    it('returns byteLength for Int16Array (ArrayBufferView)', () => {
      expect(estimateHttpBodyByteLength(new Int16Array([1, 2, 3]))).toBe(6);
    });
  });

  describe('Document (typical XHR responseType document)', () => {
    it('returns UTF-8 byte length of serialized markup when DOM APIs exist', () => {
      if (
        typeof Document === 'undefined' ||
        typeof XMLSerializer === 'undefined'
      ) {
        expect(true).toBe(true);
        return;
      }
      const doc = new Document();
      const root = doc.createElement('root');
      root.textContent = 'hi';
      doc.appendChild(root);
      const n = estimateHttpBodyByteLength(doc);
      expect(n).toBeGreaterThan(0);
      expect(n).toBe(
        new TextEncoder().encode(new XMLSerializer().serializeToString(doc))
          .length
      );
    });

    it('returns undefined for empty serialized document', () => {
      if (
        typeof Document === 'undefined' ||
        typeof XMLSerializer === 'undefined'
      ) {
        expect(true).toBe(true);
        return;
      }
      const doc = new Document();
      jest
        .spyOn(XMLSerializer.prototype, 'serializeToString')
        .mockReturnValue('');
      expect(estimateHttpBodyByteLength(doc)).toBeUndefined();
      jest.restoreAllMocks();
    });
  });

  describe('fallback: types not measured', () => {
    it('returns undefined for plain object (e.g. responseType json)', () => {
      expect(estimateHttpBodyByteLength({ ok: true })).toBeUndefined();
    });

    it('returns undefined for number', () => {
      expect(estimateHttpBodyByteLength(42)).toBeUndefined();
    });

    it('returns undefined for boolean', () => {
      expect(estimateHttpBodyByteLength(true)).toBeUndefined();
    });

    it('returns undefined for FormData when present', () => {
      if (typeof FormData === 'undefined') {
        expect(true).toBe(true);
        return;
      }
      const fd = new FormData();
      fd.append('a', 'b');
      expect(estimateHttpBodyByteLength(fd)).toBeUndefined();
    });

    it('returns undefined for URLSearchParams when present', () => {
      if (typeof URLSearchParams === 'undefined') {
        expect(true).toBe(true);
        return;
      }
      const params = new URLSearchParams({ x: '1', y: '2' });
      expect(estimateHttpBodyByteLength(params)).toBeUndefined();
    });
  });

  describe('TextEncoder failure path', () => {
    it('returns undefined when encode throws', () => {
      const encodeSpy = jest
        .spyOn(TextEncoder.prototype, 'encode')
        .mockImplementation(() => {
          throw new Error('encode failed');
        });
      expect(estimateHttpBodyByteLength('not-empty')).toBeUndefined();
      encodeSpy.mockRestore();
    });
  });
});
