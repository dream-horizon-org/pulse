# syntax=docker/dockerfile:1

ARG DEBIAN_VERSION=12

############################
# 1) Build stage (Debian 12)
############################
FROM debian:${DEBIAN_VERSION} AS builder

ARG VECTOR_REPO=https://github.com/hemanthbee/vector
ARG VECTOR_REF=add-parquet

ENV DEBIAN_FRONTEND=noninteractive
ENV CARGO_HOME=/usr/local/cargo
ENV RUSTUP_HOME=/usr/local/rustup
ENV PATH=/usr/local/cargo/bin:/usr/local/rustup/toolchains/stable-x86_64-unknown-linux-gnu/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

# Build deps (Debian/Ubuntu example deps are documented; we add a few common Rust/native deps)
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    curl \
    git \
    build-essential \
    cmake \
    pkg-config \
    libssl-dev \
    clang \
    unzip \
    python3 \
    libsasl2-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src

# Clone your fork + branch
RUN git clone --depth 1 --branch "${VECTOR_REF}" "${VECTOR_REPO}" vector
WORKDIR /src/vector

# Install Rust (stable) as in Vector docs
RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --default-toolchain stable

# Install protoc using Vector's helper script (per docs)
RUN ./scripts/environment/install-protoc.sh

# Build Vector (release)
RUN make build

############################
# 2) Runtime stage (Debian 12)
############################
FROM debian:${DEBIAN_VERSION}-slim AS runtime

ENV DEBIAN_FRONTEND=noninteractive

# Runtime deps (TLS + certs; keep it minimal)
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN useradd -r -u 1000 -g root -s /usr/sbin/nologin vector

# Vector conventions
WORKDIR /etc/vector
RUN mkdir -p /var/lib/vector && chown -R vector:root /var/lib/vector /etc/vector

# Copy the built binary
COPY --from=builder /src/vector/target/release/vector /usr/bin/vector

USER vector

# Default ports people commonly expose (API is optional)
EXPOSE 8686

ENTRYPOINT ["/usr/bin/vector"]
CMD ["--config", "/etc/vector/vector.yaml"]