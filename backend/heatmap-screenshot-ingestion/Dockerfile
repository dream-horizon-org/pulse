FROM node:20

WORKDIR /app

RUN apt-get update && \
    apt-get install -y python3 build-essential libcurl4-openssl-dev && \
    rm -rf /var/lib/apt/lists/*

COPY package.json package-lock.json ./
RUN npm ci

COPY tsconfig.json ./
COPY src ./src
RUN npm run build

CMD ["node", "dist/index.js"]
