FROM rust:1.85-bookworm as builder

WORKDIR /app

# Install build dependencies for rdkafka (cmake-build)
RUN apt-get update && apt-get install -y cmake build-essential && rm -rf /var/lib/apt/lists/*

COPY Cargo.toml ./

# Create dummy main.rs to cache dependency compilation
RUN mkdir src && echo "fn main() {}" > src/main.rs
RUN cargo build --release 2>/dev/null || true

# Remove dummy artifacts so cargo detects source change
RUN rm -rf src target/release/deps/pulse_session_capture* target/release/pulse-session-capture

# Now copy real source and build
COPY src ./src
RUN touch src/main.rs && cargo build --release

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y ca-certificates curl && rm -rf /var/lib/apt/lists/*
COPY --from=builder /app/target/release/pulse-session-capture /usr/local/bin/

EXPOSE 3400
ENV PORT=3400
CMD ["pulse-session-capture"]
