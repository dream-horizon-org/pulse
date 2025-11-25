module.exports = {
  preset: 'react-native',
  modulePathIgnorePatterns: [
    '<rootDir>/example/node_modules',
    '<rootDir>/lib/',
  ],
  testEnvironment: 'node',
  transform: {
    '^.+\\.(js|jsx|ts|tsx)$': [
      'babel-jest',
      { configFile: './babel.config.js' },
    ],
  },
  transformIgnorePatterns: ['node_modules/(?!(react-native|@react-native)/)'],
  moduleFileExtensions: ['ts', 'tsx', 'js', 'jsx', 'json'],
  collectCoverageFrom: [
    'src/**/*.{ts,tsx}',
    '!src/**/*.d.ts',
    '!src/__tests__/**',
  ],
};
