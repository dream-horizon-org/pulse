# Multi-stage build for pulse-ui (React)
FROM node:20-alpine AS builder

# Set working directory
WORKDIR /app

# Copy package files
COPY package.json yarn.lock ./

# Install dependencies using yarn
RUN yarn install --frozen-lockfile

# Copy source code and configuration
COPY . .

# Build arguments for environment variables
ARG REACT_APP_GOOGLE_CLIENT_ID
ENV REACT_APP_GOOGLE_CLIENT_ID=$REACT_APP_GOOGLE_CLIENT_ID

ARG REACT_APP_PULSE_SERVER_URL
ENV REACT_APP_PULSE_SERVER_URL=$REACT_APP_PULSE_SERVER_URL

ARG REACT_APP_GOOGLE_OAUTH_ENABLED
ENV REACT_APP_GOOGLE_OAUTH_ENABLED=$REACT_APP_GOOGLE_OAUTH_ENABLED

ARG REACT_APP_FIREBASE_API_KEY
ENV REACT_APP_FIREBASE_API_KEY=$REACT_APP_FIREBASE_API_KEY

ARG REACT_APP_FIREBASE_AUTH_DOMAIN
ENV REACT_APP_FIREBASE_AUTH_DOMAIN=$REACT_APP_FIREBASE_AUTH_DOMAIN

ARG REACT_APP_FIREBASE_PROJECT_ID
ENV REACT_APP_FIREBASE_PROJECT_ID=$REACT_APP_FIREBASE_PROJECT_ID

ARG REACT_APP_FIREBASE_APP_ID
ENV REACT_APP_FIREBASE_APP_ID=$REACT_APP_FIREBASE_APP_ID

ARG REACT_APP_USE_MOCK_SERVER
ENV REACT_APP_USE_MOCK_SERVER=$REACT_APP_USE_MOCK_SERVER

ARG REACT_APP_MOCK_DELAY
ENV REACT_APP_MOCK_DELAY=$REACT_APP_MOCK_DELAY

ARG REACT_APP_MOCK_ERROR_RATE
ENV REACT_APP_MOCK_ERROR_RATE=$REACT_APP_MOCK_ERROR_RATE

ARG REACT_APP_MOCK_LOGGING
ENV REACT_APP_MOCK_LOGGING=$REACT_APP_MOCK_LOGGING

ARG REACT_APP_FIREBASE_AUTH_EMULATOR
ENV REACT_APP_FIREBASE_AUTH_EMULATOR=$REACT_APP_FIREBASE_AUTH_EMULATOR

ARG REACT_APP_FIREBASE_AUTH_EMULATOR_URL
ENV REACT_APP_FIREBASE_AUTH_EMULATOR_URL=$REACT_APP_FIREBASE_AUTH_EMULATOR_URL

ARG NODE_ENV
ENV NODE_ENV=$NODE_ENV

# Add babel plugin to fix warning
ENV BABEL_ENV=production

# Increase Node heap to avoid OOM during production builds
ENV NODE_OPTIONS="--max-old-space-size=3072"

# Build the application
RUN yarn build

# Production stage with nginx (non-root)
FROM nginx:alpine

COPY --from=builder /app/build /usr/share/nginx/html

# Prepare directories and config so nginx can run as non-root on port 8080
RUN echo 'server {' > /etc/nginx/conf.d/default.conf && \
    echo '    listen 8080;' >> /etc/nginx/conf.d/default.conf && \
    echo '    location / {' >> /etc/nginx/conf.d/default.conf && \
    echo '        root /usr/share/nginx/html;' >> /etc/nginx/conf.d/default.conf && \
    echo '        index index.html index.htm;' >> /etc/nginx/conf.d/default.conf && \
    echo '        try_files $uri $uri/ /index.html;' >> /etc/nginx/conf.d/default.conf && \
    echo '    }' >> /etc/nginx/conf.d/default.conf && \
    echo '    location /healthcheck.txt {' >> /etc/nginx/conf.d/default.conf && \
    echo '        root /usr/share/nginx/html;' >> /etc/nginx/conf.d/default.conf && \
    echo '    }' >> /etc/nginx/conf.d/default.conf && \
    echo '}' >> /etc/nginx/conf.d/default.conf && \
    sed -i 's|/run/nginx.pid|/tmp/nginx.pid|' /etc/nginx/nginx.conf && \
    chown -R nginx:nginx /var/cache/nginx /var/log/nginx /etc/nginx/conf.d && \
    chmod -R g+w /var/cache/nginx /var/log/nginx /tmp

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=10s --retries=3 \
  CMD wget --spider -q http://127.0.0.1:8080/healthcheck.txt || exit 1

USER nginx

CMD ["nginx", "-g", "daemon off;"]