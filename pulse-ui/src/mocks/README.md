# Mock Server Module

This module provides mock responses for all API endpoints, allowing development without backend dependencies.

## Quick Start

1. **Enable Mock Server**
   Add to your `.env.development` or `.env.local`:

   ```bash
   REACT_APP_USE_MOCK_SERVER=true
   REACT_APP_MOCK_DELAY=500
   REACT_APP_MOCK_ERROR_RATE=0.1
   REACT_APP_MOCK_LOGGING=true
   ```

2. **Disable Mock Server**
   Set `REACT_APP_USE_MOCK_SERVER=false` or remove the environment variable.

3. **Remove Mock Server**
   Delete the entire `src/mocks` directory and remove the mock server code from `makeRequestToServer.ts`.

## Configuration Options

| Environment Variable        | Default | Description                     |
| --------------------------- | ------- | ------------------------------- |
| `REACT_APP_USE_MOCK_SERVER` | `false` | Enable/disable mock server      |
| `REACT_APP_MOCK_DELAY`      | `500`   | Response delay in milliseconds  |
| `REACT_APP_MOCK_ERROR_RATE` | `0.1`   | Error simulation rate (0.0-1.0) |
| `REACT_APP_MOCK_LOGGING`    | `false` | Enable console logging          |

## Features

- **Lazy Loading**: Mock server only loads when enabled
- **Realistic Data**: Pre-populated with realistic mock data
- **Error Simulation**: Random error responses for testing
- **Network Simulation**: Configurable response delays
- **State Management**: Maintains state across API calls
- **Easy Removal**: Can be completely removed without affecting production code

## API Coverage

The mock server covers all major API categories:

- ✅ Authentication & User Management
- ✅ Job Management
- ✅ Alerts Management
- ✅ Analytics & Metrics
- ✅ Universal Query System
- ✅ Events & Data
- ✅ AI Features

## Usage

The mock server automatically intercepts API calls when enabled. No code changes needed in your components or hooks.

## Development

To add new mock responses:

1. Add endpoint handler in `MockResponseGenerator.ts`
2. Add mock data in `MockDataStore.ts`
3. Update response types in `responses/` directory

## Troubleshooting

- **Mock server not working**: Check environment variables
- **Missing responses**: Add handler in `MockResponseGenerator.ts`
- **Type errors**: Update interfaces in `types.ts`
