import {
  buildPulseInitializationCode,
  PULSE_IMPORT,
  ATTRIBUTES_IMPORT,
} from '../utils';
import type { PulseAttributes } from '../types';

describe('PULSE_IMPORT', () => {
  it('should contain the correct import statement', () => {
    expect(PULSE_IMPORT).toBe('import com.pulsereactnativeotel.Pulse\n');
  });
});

describe('ATTRIBUTES_IMPORT', () => {
  it('should contain the correct import statements', () => {
    expect(ATTRIBUTES_IMPORT).toContain(
      'import io.opentelemetry.api.common.Attributes'
    );
    expect(ATTRIBUTES_IMPORT).toContain(
      'import io.opentelemetry.api.common.AttributeKey'
    );
  });
});

describe('buildPulseInitializationCode', () => {
  describe('basic initialization', () => {
    it('should generate basic initialization code with only endpointBaseUrl', () => {
      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
      });

      expect(result).toContain('Pulse.initialize');
      expect(result).toContain('this,');
      expect(result).toContain('"http://localhost:4318"');
      expect(result).not.toContain('endpointHeaders');
      expect(result).not.toContain('globalAttributes');
      expect(result).toMatch(/\)\s*\{[\s\S]*\}/);
    });

    it('should handle URLs with special characters', () => {
      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'https://api.example.com/v1/endpoint',
      });

      expect(result).toContain('"https://api.example.com/v1/endpoint"');
    });
  });

  describe('endpointHeaders', () => {
    it('should include endpointHeaders when provided', () => {
      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        endpointHeaders: {
          'Authorization': 'Bearer token123',
          'X-API-Key': 'api-key-456',
        },
      });

      expect(result).toContain('endpointHeaders = mapOf(');
      expect(result).toContain('"Authorization" to "Bearer token123"');
      expect(result).toContain('"X-API-Key" to "api-key-456"');
    });

    it('should handle empty endpointHeaders object', () => {
      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        endpointHeaders: {},
      });

      expect(result).not.toContain('endpointHeaders');
    });

    it('should include special characters in header keys and values as-is (might cause Kotlin compilation errors)', () => {
      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        endpointHeaders: {
          'Header-With"Quotes': 'Value\\With\\Backslashes',
          'Header\nWith\nNewlines': 'Value\tWith\tTabs',
        },
      });

      expect(result).toContain('endpointHeaders = mapOf');
      expect(result).toContain('Header-With"Quotes');
      expect(result).toContain('Value\\With\\Backslashes');
    });

    it('should handle single header', () => {
      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        endpointHeaders: {
          Authorization: 'Bearer token',
        },
      });

      expect(result).toContain(
        'endpointHeaders = mapOf("Authorization" to "Bearer token")'
      );
    });

    it('should handle multiple headers in correct format', () => {
      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        endpointHeaders: {
          Header1: 'Value1',
          Header2: 'Value2',
          Header3: 'Value3',
        },
      });

      const headerSection =
        result.match(/endpointHeaders = mapOf\(([^)]+)\)/)?.[1] || '';
      expect(headerSection).toContain('"Header1" to "Value1"');
      expect(headerSection).toContain('"Header2" to "Value2"');
      expect(headerSection).toContain('"Header3" to "Value3"');
    });
  });

  describe('globalAttributes', () => {
    it('should include globalAttributes when provided with string values', () => {
      const attributes: PulseAttributes = {
        'deployment.environment': 'production',
        'app.version': '1.0.0',
      };

      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        globalAttributes: attributes,
      });

      expect(result).toContain('globalAttributes = {');
      expect(result).toContain('Attributes.builder().apply {');
      expect(result).toContain(
        'put(AttributeKey.stringKey("deployment.environment"), "production")'
      );
      expect(result).toContain(
        'put(AttributeKey.stringKey("app.version"), "1.0.0")'
      );
    });

    it('should format attributes with each key-value pair on separate lines', () => {
      const attributes: PulseAttributes = {
        key1: 'value1',
        key2: 'value2',
        key3: 'value3',
      };

      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        globalAttributes: attributes,
      });

      expect(result).toContain('globalAttributes = {');
      expect(result).toContain('Attributes.builder().apply {');

      expect(result).toContain('put(AttributeKey.stringKey("key1"), "value1")');
      expect(result).toContain('"value1"');
      expect(result).toContain('AttributeKey.stringKey("key2")');
      expect(result).toContain('"value2"');
      expect(result).toContain('AttributeKey.stringKey("key3")');
      expect(result).toContain('"value3"');

      const lines = result.split('\n');
      const key1LineIndex = lines.findIndex((line) => line.includes('key1'));
      const key2LineIndex = lines.findIndex((line) => line.includes('key2'));
      const key3LineIndex = lines.findIndex((line) => line.includes('key3'));

      expect(key1LineIndex).toBeGreaterThan(-1);
      expect(key2LineIndex).toBeGreaterThan(-1);
      expect(key3LineIndex).toBeGreaterThan(-1);
      expect(key1LineIndex).not.toBe(key2LineIndex);
      expect(key2LineIndex).not.toBe(key3LineIndex);

      if (key1LineIndex >= 0) {
        const key1Line = lines[key1LineIndex];
        expect(key1Line).toContain('AttributeKey.stringKey("key1")');
        expect(key1Line).toContain('"value1"');
      }
    });

    it('should handle integer number values', () => {
      const attributes: PulseAttributes = {
        'user.id': 12345,
        'count': 0,
        'negative': -100,
      };

      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        globalAttributes: attributes,
      });

      expect(result).toContain('AttributeKey.longKey("user.id")');
      expect(result).toContain('12345L');
      expect(result).toContain('AttributeKey.longKey("count")');
      expect(result).toContain('0L');
      expect(result).toContain('AttributeKey.longKey("negative")');
      expect(result).toContain('-100L');
    });

    it('should handle floating point number values', () => {
      const attributes: PulseAttributes = {
        price: 99.99,
        ratio: 0.5,
        negative: -10.5,
      };

      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        globalAttributes: attributes,
      });

      expect(result).toContain('AttributeKey.doubleKey("price")');
      expect(result).toContain('99.99');
      expect(result).toContain('AttributeKey.doubleKey("ratio")');
      expect(result).toContain('0.5');
      expect(result).toContain('AttributeKey.doubleKey("negative")');
      expect(result).toContain('-10.5');
    });

    it('should handle boolean values', () => {
      const attributes: PulseAttributes = {
        isDebug: true,
        isProduction: false,
      };

      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        globalAttributes: attributes,
      });

      expect(result).toContain('AttributeKey.booleanKey("isDebug")');
      expect(result).toContain('true');
      expect(result).toContain('AttributeKey.booleanKey("isProduction")');
      expect(result).toContain('false');
    });

    it('should handle string arrays', () => {
      const attributes: PulseAttributes = {
        tags: ['production', 'v1', 'stable'],
        environments: ['dev', 'staging'],
      };

      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        globalAttributes: attributes,
      });

      expect(result).toContain('AttributeKey.stringArrayKey("tags")');
      expect(result).toContain('listOf("production", "v1", "stable")');
      expect(result).toContain('AttributeKey.stringArrayKey("environments")');
      expect(result).toContain('listOf("dev", "staging")');
    });

    it('should handle integer arrays', () => {
      const attributes: PulseAttributes = {
        scores: [100, 200, 300],
        ids: [1, 2, 3, 4, 5],
      };

      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        globalAttributes: attributes,
      });

      expect(result).toContain('AttributeKey.longArrayKey("scores")');
      expect(result).toContain('listOf(100L, 200L, 300L)');
      expect(result).toContain('AttributeKey.longArrayKey("ids")');
      expect(result).toContain('listOf(1L, 2L, 3L, 4L, 5L)');
    });

    it('should handle mixed number arrays (floats) as double arrays', () => {
      const attributes: PulseAttributes = {
        prices: [10.5, 20.0, 30.75],
        mixed: [1, 2.5, 3],
      };

      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        globalAttributes: attributes,
      });

      expect(result).toContain('AttributeKey.doubleArrayKey("prices")');
      expect(result).toContain('listOf(10.5, 20.0, 30.75)');
      expect(result).toContain('AttributeKey.doubleArrayKey("mixed")');
    });

    it('should handle boolean arrays', () => {
      const attributes: PulseAttributes = {
        flags: [true, false, true],
        options: [false],
      };

      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        globalAttributes: attributes,
      });

      expect(result).toContain('AttributeKey.booleanArrayKey("flags")');
      expect(result).toContain('listOf(true, false, true)');
      expect(result).toContain('AttributeKey.booleanArrayKey("options")');
      expect(result).toContain('listOf(false)');
    });

    it('should skip null and undefined values', () => {
      const attributes: PulseAttributes = {
        valid: 'value',
        nullValue: null,
        undefinedValue: undefined,
        anotherValid: 123,
      };

      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        globalAttributes: attributes,
      });

      expect(result).toContain('AttributeKey.stringKey("valid")');
      expect(result).toContain('AttributeKey.longKey("anotherValid")');
      expect(result).not.toContain('nullValue');
      expect(result).not.toContain('undefinedValue');
    });

    it('should skip empty arrays', () => {
      const attributes: PulseAttributes = {
        valid: 'value',
        emptyStringArray: [],
        emptyNumberArray: [],
        emptyBooleanArray: [],
      };

      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        globalAttributes: attributes,
      });

      expect(result).toContain('AttributeKey.stringKey("valid")');
      expect(result).not.toContain('emptyStringArray');
      expect(result).not.toContain('emptyNumberArray');
      expect(result).not.toContain('emptyBooleanArray');
    });

    it('should handle empty globalAttributes object', () => {
      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        globalAttributes: {},
      });

      expect(result).not.toContain('globalAttributes');
    });

    it('should include special characters in attribute keys and string values as-is (will cause Kotlin compilation errors)', () => {
      const attributes: PulseAttributes = {
        'key"with"quotes': 'value\\with\\backslashes',
        'key\nwith\nnewlines': 'value\twith\ttabs',
      };

      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        globalAttributes: attributes,
      });

      expect(result).toContain('globalAttributes = {');
      expect(result).toContain('Attributes.builder().apply {');
      expect(result).toContain('key"with"quotes');
      expect(result).toContain('value\\with\\backslashes');
    });

    it('should handle mixed attribute types', () => {
      const attributes: PulseAttributes = {
        string: 'value',
        number: 42,
        float: 3.14,
        boolean: true,
        stringArray: ['a', 'b'],
        numberArray: [1, 2, 3],
        booleanArray: [true, false],
      };

      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        globalAttributes: attributes,
      });

      expect(result).toContain('AttributeKey.stringKey("string")');
      expect(result).toContain('AttributeKey.longKey("number")');
      expect(result).toContain('AttributeKey.doubleKey("float")');
      expect(result).toContain('AttributeKey.booleanKey("boolean")');
      expect(result).toContain('AttributeKey.stringArrayKey("stringArray")');
      expect(result).toContain('AttributeKey.longArrayKey("numberArray")');
      expect(result).toContain('AttributeKey.booleanArrayKey("booleanArray")');
    });

    it('should not include globalAttributes when all values are null/undefined/empty', () => {
      const attributes: PulseAttributes = {
        nullValue: null,
        undefinedValue: undefined,
        emptyArray: [],
      };

      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        globalAttributes: attributes,
      });

      expect(result).not.toContain('globalAttributes');
    });

    it('should not include globalAttributes when explicitly empty object', () => {
      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        globalAttributes: {},
      });

      expect(result).not.toContain('globalAttributes');
      expect(result).not.toMatch(/globalAttributes\s*=\s*\{\s*\}/);
    });
  });

  describe('instrumentation', () => {
    it('should include interaction instrumentation with URL', () => {
      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        instrumentation: {
          interaction: {
            enabled: true,
            url: 'http://localhost:8080/v1/interactions',
          },
        },
      });

      expect(result).toContain(
        'interaction { enabled(true); setConfigUrl { "http://localhost:8080/v1/interactions" } }'
      );
    });

    it('should include interaction instrumentation without URL', () => {
      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        instrumentation: {
          interaction: {
            enabled: false,
          },
        },
      });

      expect(result).toContain('interaction { enabled(false) }');
      expect(result).not.toContain('setConfigUrl');
    });

    it('should include activity instrumentation', () => {
      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        instrumentation: {
          activity: true,
        },
      });

      expect(result).toContain('activity { enabled(true) }');
    });

    it('should include network instrumentation', () => {
      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        instrumentation: {
          network: false,
        },
      });

      expect(result).toContain('networkMonitoring { enabled(false) }');
    });

    it('should include ANR instrumentation', () => {
      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        instrumentation: {
          anr: true,
        },
      });

      expect(result).toContain('anrReporter { enabled(true) }');
    });

    it('should include slow rendering instrumentation', () => {
      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        instrumentation: {
          slowRendering: false,
        },
      });

      expect(result).toContain('slowRenderingReporter { enabled(false) }');
    });

    it('should include fragment instrumentation', () => {
      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        instrumentation: {
          fragment: true,
        },
      });

      expect(result).toContain('fragment { enabled(true) }');
    });

    it('should include crash instrumentation', () => {
      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        instrumentation: {
          crash: false,
        },
      });

      expect(result).toContain('crashReporter { enabled(false) }');
    });

    it('should include all instrumentation options', () => {
      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        instrumentation: {
          interaction: {
            enabled: true,
            url: 'http://localhost:8080/v1/interactions',
          },
          activity: true,
          network: false,
          anr: true,
          slowRendering: false,
          fragment: true,
          crash: false,
        },
      });

      expect(result).toContain(
        'interaction { enabled(true); setConfigUrl { "http://localhost:8080/v1/interactions" } }'
      );
      expect(result).toContain('activity { enabled(true) }');
      expect(result).toContain('networkMonitoring { enabled(false) }');
      expect(result).toContain('anrReporter { enabled(true) }');
      expect(result).toContain('slowRenderingReporter { enabled(false) }');
      expect(result).toContain('fragment { enabled(true) }');
      expect(result).toContain('crashReporter { enabled(false) }');
    });

    it('should not include instrumentation section when not provided', () => {
      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
      });

      expect(result).toContain(') {');
      expect(result).toContain('}');
      expect(result).not.toContain('interaction');
      expect(result).not.toContain('activity');
      expect(result).not.toContain('networkMonitoring');
    });
  });

  describe('combined options', () => {
    it('should handle all options together', () => {
      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'https://api.example.com:4318',
        endpointHeaders: {
          'Authorization': 'Bearer token',
          'X-API-Key': 'key123',
        },
        globalAttributes: {
          'deployment.environment': 'production',
          'app.version': '1.0.0',
          'user.id': 12345,
        },
        instrumentation: {
          interaction: {
            enabled: true,
            url: 'http://localhost:8080/v1/interactions',
          },
          activity: true,
          network: true,
        },
      });

      expect(result).toContain('Pulse.initialize');
      expect(result).toContain('"https://api.example.com:4318"');
      expect(result).toContain('endpointHeaders = mapOf(');
      expect(result).toContain('globalAttributes = {');
      expect(result).toContain(
        'interaction { enabled(true); setConfigUrl { "http://localhost:8080/v1/interactions" } }'
      );
      expect(result).toContain('activity { enabled(true) }');
      expect(result).toContain('networkMonitoring { enabled(true) }');
    });

    it('should maintain correct code structure and formatting', () => {
      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        endpointHeaders: {
          Header1: 'Value1',
        },
        globalAttributes: {
          key1: 'value1',
          key2: 'value2',
        },
        instrumentation: {
          activity: true,
        },
      });

      // Check structure
      expect(result).toMatch(/Pulse\.initialize\s*\(/);
      expect(result).toMatch(/this,/);
      expect(result).toMatch(/"[^"]+"/); // endpointBaseUrl
      expect(result).toMatch(/endpointHeaders\s*=/);
      expect(result).toMatch(/globalAttributes\s*=/);
      expect(result).toMatch(/\)\s*\{/);
      expect(result).toMatch(/\}\s*$/);
    });
  });

  describe('edge cases', () => {
    it('should handle very long URLs', () => {
      const longUrl = 'http://' + 'a'.repeat(1000) + '.com';
      const result = buildPulseInitializationCode({
        endpointBaseUrl: longUrl,
      });

      expect(result).toContain(`"${longUrl}"`);
    });

    it('should handle very long header values', () => {
      const longValue = 'a'.repeat(1000);
      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        endpointHeaders: {
          LongHeader: longValue,
        },
      });

      expect(result).toContain(`"${longValue}"`);
    });

    it('should handle many attributes', () => {
      const attributes: PulseAttributes = {};
      for (let i = 0; i < 100; i++) {
        attributes[`key${i}`] = `value${i}`;
      }

      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        globalAttributes: attributes,
      });

      for (let i = 0; i < 100; i++) {
        expect(result).toContain(`AttributeKey.stringKey("key${i}")`);
        expect(result).toContain(`"value${i}"`);
      }
    });

    it('should handle numeric string values that look like numbers', () => {
      const attributes: PulseAttributes = {
        version: '1.0.0',
        build: '123',
      };

      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        globalAttributes: attributes,
      });

      expect(result).toContain('AttributeKey.stringKey("version")');
      expect(result).toContain('"1.0.0"');
      expect(result).toContain('AttributeKey.stringKey("build")');
      expect(result).toContain('"123"');
    });

    it('should handle zero and negative numbers correctly', () => {
      const attributes: PulseAttributes = {
        zero: 0,
        negative: -100,
        negativeFloat: -10.5,
      };

      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        globalAttributes: attributes,
      });

      expect(result).toContain('0L');
      expect(result).toContain('-100L');
      expect(result).toContain('-10.5');
    });

    it('should handle array with single element', () => {
      const attributes: PulseAttributes = {
        single: ['value'],
        singleNumber: [42],
        singleBoolean: [true],
      };

      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        globalAttributes: attributes,
      });

      expect(result).toContain('listOf("value")');
      expect(result).toContain('listOf(42L)');
      expect(result).toContain('listOf(true)');
    });

    it('should handle very large numbers', () => {
      const attributes: PulseAttributes = {
        largeInt: Number.MAX_SAFE_INTEGER,
        largeFloat: Number.MAX_VALUE,
      };

      const result = buildPulseInitializationCode({
        endpointBaseUrl: 'http://localhost:4318',
        globalAttributes: attributes,
      });

      expect(result).toContain(`${Number.MAX_SAFE_INTEGER}L`);
      expect(result).toContain(`${Number.MAX_VALUE}`);
    });
  });
});
