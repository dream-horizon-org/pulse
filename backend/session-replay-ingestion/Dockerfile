FROM node:20

WORKDIR /app

# Install build dependencies for native modules (node-rdkafka needs librdkafka + libcurl)
RUN apt-get update && \
    apt-get install -y python3 build-essential libcurl4-openssl-dev && \
    rm -rf /var/lib/apt/lists/*

COPY package.json ./
RUN npm install

COPY tsconfig.json ./
COPY src ./src
RUN npm run build

CMD ["node", "dist/index.js"]
