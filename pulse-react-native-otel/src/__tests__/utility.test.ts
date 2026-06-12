import { getAbsoluteUrl, nowMs } from '../utility';

describe('nowMs', () => {
  beforeEach(() => {
    // Reset global.performance before each test
    delete (global as any).performance;
  });

  it('returns Date.now() when performance.now is not available', () => {
    const before = Date.now();
    const result = nowMs();
    const after = Date.now();

    expect(result).toBeGreaterThanOrEqual(before);
    expect(result).toBeLessThanOrEqual(after);
    expect(typeof result).toBe('number');
  });

  it('returns performance.now() when available', () => {
    const mockPerformance = {
      now: jest.fn(() => 1234.567),
    };
    (global as any).performance = mockPerformance;

    const result = nowMs();

    expect(result).toBe(1234.567);
    expect(mockPerformance.now).toHaveBeenCalledTimes(1);
  });

  it('returns performance.now() when performance exists but now is not a function', () => {
    const mockPerformance = {
      now: 'not a function',
    };
    (global as any).performance = mockPerformance;

    const before = Date.now();
    const result = nowMs();
    const after = Date.now();

    expect(result).toBeGreaterThanOrEqual(before);
    expect(result).toBeLessThanOrEqual(after);
  });

  it('handles performance.now() returning different values on subsequent calls', () => {
    let callCount = 0;
    const mockPerformance = {
      now: jest.fn(() => {
        callCount++;
        return callCount * 100;
      }),
    };
    (global as any).performance = mockPerformance;

    const result1 = nowMs();
    const result2 = nowMs();

    expect(result1).toBe(100);
    expect(result2).toBe(200);
    expect(mockPerformance.now).toHaveBeenCalledTimes(2);
  });

  it('returns a number in milliseconds', () => {
    const result = nowMs();
    expect(typeof result).toBe('number');
    expect(Number.isFinite(result)).toBe(true);
  });
});

describe('getAbsoluteUrl', () => {
  it.each([
    {
      input: 'http://pulse.com',
      baseUrl: 'http://pulse.com',
      expected: 'http://pulse.com',
    },
    {
      input: 'https://pulse.com/test/',
      baseUrl: 'https://pulse.com',
      expected: 'https://pulse.com/test/',
    },
    {
      input: 'https://somewhere-else.com/test/',
      baseUrl: 'https://pulse.com',
      expected: 'https://somewhere-else.com/test/',
    },
    {
      input: '/test/page',
      baseUrl: 'http://pulse.com',
      expected: 'http://pulse.com/test/page',
    },
    {
      input: '/test/page/',
      baseUrl: 'http://pulse.com',
      expected: 'http://pulse.com/test/page/',
    },
    {
      input: '/test/page',
      baseUrl: 'http://pulse.com/home/dashboard/',
      expected: 'http://pulse.com/test/page',
    },
    {
      input: '/test/page',
      baseUrl: 'http://pulse.com/home/dashboard',
      expected: 'http://pulse.com/test/page',
    },
    {
      input: 'test/page',
      baseUrl: 'http://pulse.com/home/dashboard',
      expected: 'http://pulse.com/home/test/page',
    },
    {
      input: 'test/page',
      baseUrl: 'http://pulse.com/home/dashboard/',
      expected: 'http://pulse.com/home/dashboard/test/page',
    },
    {
      input: '//test.com',
      baseUrl: 'http://pulse.com',
      expected: 'http://test.com',
    },
    {
      input: '//test.com',
      baseUrl: 'https://pulse.com',
      expected: 'https://test.com',
    },
    {
      input: '//test.com',
      baseUrl: 'http://pulse.com/home/dashboard/',
      expected: 'http://test.com',
    },
    {
      input: '//test.com/test/page',
      baseUrl: 'http://pulse.com/home/dashboard/',
      expected: 'http://test.com/test/page',
    },
    {
      input: './test/page',
      baseUrl: 'http://pulse.com',
      expected: 'http://pulse.com/test/page',
    },
    {
      input: './test/page',
      baseUrl: 'http://pulse.com/home/dashboard/',
      expected: 'http://pulse.com/home/dashboard/test/page',
    },
    {
      input: '../test/page',
      baseUrl: 'http://pulse.com/home/dashboard/',
      expected: 'http://pulse.com/home/test/page',
    },
    {
      input: '../../test/page',
      baseUrl: 'http://pulse.com/home/dashboard/',
      expected: 'http://pulse.com/test/page',
    },
    {
      input: 'test/img.png',
      baseUrl: 'file:///Documents/folder/file.txt',
      expected: 'file:///Documents/folder/test/img.png',
    },
    {
      input: '/test/page',
      baseUrl: 'invalid:base',
      expected: '/test/page',
    },
    {
      input: 'http://pulse.com',
      expected: 'http://pulse.com',
    },
    {
      input: 'https://pulse.com/test/',
      expected: 'https://pulse.com/test/',
    },
    {
      input: 'https://somewhere-else.com/test/',
      expected: 'https://somewhere-else.com/test/',
    },
    {
      input: '/test/page',
      expected: '/test/page',
    },
  ])(
    "returns '$expected' for URL '$input' and base URL '$baseUrl'",
    ({ input, baseUrl, expected }) => {
      if (baseUrl !== undefined) {
        expect(getAbsoluteUrl(input, baseUrl)).toEqual(expected);
      } else {
        expect(getAbsoluteUrl(input)).toEqual(expected);
      }
    }
  );
});
