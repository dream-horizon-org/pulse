# Pulse UI - Frontend Application

<div align="center">

**Modern React-based web interface for the Pulse Observability Platform**

[![React](https://img.shields.io/badge/React-18.3-blue.svg)](https://reactjs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-4.4-blue.svg)](https://www.typescriptlang.org/)
[![Mantine](https://img.shields.io/badge/Mantine-7.11-339af0.svg)](https://mantine.dev/)

</div>

---

## 📑 Table of Contents

- [Overview](#-overview)
- [Technology Stack](#-technology-stack)
- [Getting Started](#-getting-started)
- [Available Scripts](#-available-scripts)
- [Configuration](#-configuration)

## 🌟 Overview

Pulse UI is a modern, responsive web application built with React 18 and TypeScript. It provides an intuitive interface for monitoring application performance, analyzing user behavior, managing alerts, and visualizing real-time metrics.



## 🛠️ Technology Stack

### Core

- **React**: 18.3.1
- **TypeScript**: 4.4.2
- **Mantine UI**: 7.11.1 - Component library
- **TanStack Query**: 5.50.1 - Data fetching and caching
- **Zustand**: 5.0.8 - State management
- **React Router**: 6.24.1 - Routing

### Data Visualization

- **ECharts**: 6.0.0 - Interactive charts
- **Mantine DataTable**: 7.11.2 - Data tables

### Forms & Validation

- **React Hook Form**: 7.52.1 - Form management
- **React Query Builder**: 8.8.1 - Visual query builder

### Build Tools

- **Webpack**: 5.64.4 - Module bundler
- **Babel**: 7.16.0 - JavaScript compiler
- **PostCSS**: 8.4.4 - CSS processing

### Testing

- **Jest**: 27.4.3 - Testing framework
- **React Testing Library**: 13.0.0 - Component testing

### Development

- **Prettier**: 3.3.3 - Code formatting
- **ESLint**: 8.3.0 - Linting
- **Husky**: 9.0.11 - Git hooks

## 🚀 Getting Started

### Prerequisites

- Node.js 18+ and npm
- Backend server running (see [backend README](../backend/server/README.md))

### Installation

```bash
# Navigate to the project directory
cd pulse-ui

# Make .env file from .env.example
cp .env.example .env

# Install dependencies
yarn install

# Start development server
yarn start
```

The application will open at [http://localhost:3000](http://localhost:3000)

## 📜 Available Scripts

### `yarn start`

Runs the app in development mode.\
Open [http://localhost:3000](http://localhost:3000) to view it in the browser.

- Hot reload enabled
- Source maps for debugging
- Linting errors displayed in console


### `yarn build`

Builds the app for production to the `build` folder.

- Optimizes React in production mode
- Minifies code for best performance
- Generates source maps
- Includes file hashes for cache busting

```bash
yarn build

# Output will be in the 'build' directory
```

### Code Quality Scripts

```bash
# Format code with Prettier
yarn format

# Lint code with ESLint
yarn lint

# Fix linting issues
yarn lint:fix
```

## ⚙️ Configuration

### Environment Variables

Create `.env` file:

```bash
# Google OAuth
REACT_APP_GOOGLE_CLIENT_ID=your-google-client-id

# API Configuration
REACT_APP_API_BASE_URL=http://localhost:8080

# Disable google login to test to local
REACT_APP_GOOGLE_OAUTH_ENABLED=false
```

## 🔗 Related Documentation

- [Main Project README](../README.md)
- [Backend Server](../backend/server/README.md)
- [Deployment Guide](../deploy/README.md)

---
