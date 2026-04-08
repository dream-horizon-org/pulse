# Golden AMI: pulse-session-capture (Rust)

Terraform expects a **custom AMI** with the **`pulse-session-capture`** binary and a **systemd** unit named **`pulse-session-capture`**. Boot **does not** compile Rust or download the app.

## What Terraform `user-data.sh` does

1. Writes **`/etc/pulse/capture.env`** (`PORT`, `KAFKA_BROKERS`, `KAFKA_TOPIC`, `RUST_LOG`).
2. Runs **`systemctl restart pulse-session-capture`**.
3. Logs to **`/var/log/user-data.log`**.

The AMI must ship **`/etc/pulse/`** (directory exists); the env file is created on first boot.

## Systemd unit (example)

Install as **`/etc/systemd/system/pulse-session-capture.service`**:

```ini
[Unit]
Description=Pulse session replay capture (HTTP → Kafka)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
EnvironmentFile=/etc/pulse/capture.env
ExecStart=/usr/local/bin/pulse-session-capture
Restart=on-failure
RestartSec=5

# Hardening (optional)
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
```

Use **`ExecStart`** path where you actually install the binary (here matches Docker image: `/usr/local/bin/pulse-session-capture`).

## Runtime dependencies (Debian 12 / Bookworm)

The binary is built with **`rdkafka`** (`cmake-build` in `Cargo.toml`). On the AMI you need **runtime** libraries, not compilers. After copying the binary, run:

```bash
ldd /usr/local/bin/pulse-session-capture
```

Install anything reported as **`not found`**. Typical Bookworm packages:

```bash
sudo apt-get update && sudo apt-get install -y --no-install-recommends \
  ca-certificates \
  curl \
  librdkafka1 \
  libsasl2-2 \
  libssl3 \
  zlib1g
```

Adjust for **Ubuntu** or **Amazon Linux 2023** (use `yum/dnf` equivalents: `librdkafka`, `openssl-libs`, `cyrus-sasl-lib`, etc.).

## Method A — Manual bake (EC2)

1. **Launch** a temporary instance from **Debian 12** or **Ubuntu 22.04** (same family you want in prod) in a build subnet, with a role that can later create AMIs.

2. **Install build toolchain** (only on the builder if you compile on-instance; see method B for CI-built binary):

   ```bash
   sudo apt-get update && sudo apt-get install -y curl git build-essential cmake pkg-config \
     librdkafka-dev libsasl2-dev libssl-dev
   curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
   source "$HOME/.cargo/env"
   ```

3. **Build** from your repo (or copy CI artifact in step 4 instead):

   ```bash
   git clone --depth 1 --branch <your-branch> <pulse-repo-url> /tmp/pulse
   cd /tmp/pulse/backend/session-capture-service
   cargo build --release
   sudo install -m 0755 target/release/pulse-session-capture /usr/local/bin/
   ```

4. **Install runtime libs only** (if you copied a **pre-built** binary from CI, skip Rust install and only do `ldd` + `apt-get` runtime packages as above).

5. **Create systemd unit** (see example), then:

   ```bash
   sudo mkdir -p /etc/pulse
   sudo systemctl daemon-reload
   sudo systemctl enable pulse-session-capture
   ```

6. **Optional smoke test** on the bake instance:

   ```bash
   sudo tee /etc/pulse/capture.env <<'EOF'
   PORT=3400
   KAFKA_BROKERS=your-kafka:9092
   KAFKA_TOPIC=session_recording_events
   RUST_LOG=pulse_session_capture=info
   EOF
   sudo systemctl start pulse-session-capture
   curl -sf http://127.0.0.1:3400/healthcheck
   sudo systemctl stop pulse-session-capture
   ```

7. **Clean** build artifacts, **clear** sensitive test env if any, **stop** the instance.

8. **EC2 → Images → Create image** (or **`aws ec2 create-image`**). Record **AMI ID** → Terraform **`ami_id`**.

## Method B — CI binary + minimal AMI (recommended)

1. In **CI** (GitHub Actions, etc.), `cargo build --release` on **Linux** matching target OS (glibc version must be **≤** runtime AMI glibc, or build on the same AMI base).

2. Publish **`pulse-session-capture`** as a build artifact (or copy to S3).

3. On a **minimal** EC2 or Packer build:
   - Install **runtime** packages + **`ca-certificates`** + **`curl`** (health checks / ops).
   - `install` the binary to `/usr/local/bin/`.
   - Add **systemd** unit, **`systemctl enable`**, **`mkdir /etc/pulse`**.

4. **Create AMI** from that instance.

## Method C — Packer (`amazon-ebs`)

1. Use **`amazon-ebs`** builder with `source_ami` = Debian 12 or your standard base.

2. **Provisioner** `shell`:
   - `apt-get` runtime packages.
   - Download binary from S3 (`aws s3 cp`) or upload via `file` provisioner.
   - Write systemd unit, `daemon-reload`, `enable`.

3. **`create_snapshot`** → register AMI → output **`ami_id`** to Terraform variable or SSM Parameter Store.

## Post-bake checklist

- [ ] `pulse-session-capture` runs as non-root **or** you accept root (current example uses root).
- [ ] **`ldd`** clean on the binary.
- [ ] **`systemctl enable`** works; first real boot gets env from Terraform.
- [ ] Security group allows **3400** from **NLB subnets** only (not `0.0.0.0/0`).
- [ ] Instance profile allows **egress** to Kafka and whatever else you need (usually no S3 for capture).

## Updating

New release → new binary → new AMI → update **`ami_id`** in Terraform → roll ASG (instance refresh or replace LT default version).

## Related

- App source: `backend/session-capture-service/`
- Docker reference (layers): `backend/session-capture-service/Dockerfile` (builder uses `cmake`; runtime image is `debian:bookworm-slim` + `ca-certificates` + `curl` — your **dynamic** `librdkafka` deps usually require extra packages on slim; **`ldd`** is authoritative).
