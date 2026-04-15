import { defineConfig } from 'tsup';

export default defineConfig({
  entry: { index: 'src/index.ts' },
  format: ['esm', 'cjs'],
  dts: true,
  clean: true,
  sourcemap: true,
  define: {
    __SDK_VERSION__: JSON.stringify(process.env['npm_package_version'] ?? '0.0.0'),
  },
  external: ['react', 'react-dom', 'react-router-dom', 'next'],
});
