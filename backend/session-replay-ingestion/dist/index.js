"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const config_1 = require("./config");
const consumer_1 = require("./consumer");
async function main() {
    console.log('=== Pulse Session Replay Ingestion Consumer ===');
    const config = (0, config_1.loadConfig)();
    const consumer = new consumer_1.SessionReplayConsumer(config);
    // Graceful shutdown on SIGTERM/SIGINT
    const shutdown = async (signal) => {
        console.log(`\nReceived ${signal}, shutting down gracefully...`);
        await consumer.stop();
        process.exit(0);
    };
    process.on('SIGTERM', () => shutdown('SIGTERM'));
    process.on('SIGINT', () => shutdown('SIGINT'));
    try {
        await consumer.start();
    }
    catch (error) {
        console.error('Fatal error starting consumer:', error);
        process.exit(1);
    }
}
main();
//# sourceMappingURL=index.js.map