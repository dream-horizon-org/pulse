"""
Locust Load Test Script for Vector OTLP Endpoint

This script generates and sends OTLP log events to Vector for load testing.

Usage:
    # Install dependencies
    pip install locust opentelemetry-proto
    
    # Run with web UI (default: http://localhost:8089)
    locust
    
    # Run headless (no UI) - 100 users, spawn rate 10/sec, run for 5 minutes
    locust --headless -u 100 -r 10 -t 5m --host http://pulse-vector.delivr.local:4318
    
    # Run with custom endpoint
    locust --host http://pulse-vector.delivr.local:4318/v1/logs
"""

import time
import random
import gzip
from locust import task, between, events
from locust.contrib.fasthttp import FastHttpUser

try:
    from opentelemetry.proto.collector.logs.v1.logs_service_pb2 import ExportLogsServiceRequest
    from opentelemetry.proto.logs.v1.logs_pb2 import ResourceLogs, ScopeLogs, LogRecord
    from opentelemetry.proto.common.v1.common_pb2 import KeyValue, AnyValue
    from opentelemetry.proto.resource.v1.resource_pb2 import Resource
    PROTOBUF_AVAILABLE = True
except ImportError:
    PROTOBUF_AVAILABLE = False
    print("WARNING: opentelemetry-proto not installed. Install with: pip install opentelemetry-proto")


# =========================================================================
# SAMPLE DATA - Matching your Pulse schema
# =========================================================================

EVENT_NAMES = [
    "createteam", "contestjoin", "openapp", "screenview", 
    "payment_success", "payment_failed", "user_signup", "user_login"
]

PULSE_TYPES = [
    "app.launch", "screen.view", "button.click", "api.call", 
    "payment.init", "payment.success", "device.crash", "session.start"
]

SCREEN_NAMES = [
    "HomeScreen", "ProfileScreen", "SettingsScreen", "PaymentScreen",
    "ContestScreen", "TeamScreen", "WalletScreen", "HistoryScreen"
]

DEVICE_MANUFACTURERS = ["Samsung", "Xiaomi", "OnePlus", "Realme", "Vivo", "Oppo", "Google"]
DEVICE_MODELS = ["SM-G998B", "M2101K6G", "LE2111", "RMX3370", "V2111", "CPH2219", "Pixel 7"]
OS_VERSIONS = ["12.0", "13.0", "14.0"]
API_LEVELS = ["31", "33", "34"]


def create_resource_attrs() -> dict:
    """Create randomized resource attributes."""
    return {
        "service.name": "pulse-test-app",
        "android.os.api_level": random.choice(API_LEVELS),
        "os.version": random.choice(OS_VERSIONS),
        "os.name": "Android",
        "app.build_id": f"2024011500{random.randint(1, 9)}",
        "app.build_name": random.choice(["2.4.0", "2.5.0", "2.5.1"]),
        "device.manufacturer": random.choice(DEVICE_MANUFACTURERS),
        "device.model.identifier": random.choice(DEVICE_MODELS),
    }


def create_log_attributes(index: int) -> dict:
    """Create realistic log attributes matching your Pulse schema."""
    return {
        "pulse.type": random.choice(PULSE_TYPES),
        "session.id": f"sess-{random.randint(100000, 999999)}",
        "screen.name": random.choice(SCREEN_NAMES),
        "pulse.app_state": random.choice(["foreground", "background"]),
        "network.carrier.mcc": str(random.randint(400, 410)),
        "network.carrier.mnc": str(random.randint(1, 99)),
        "network.carrier.icc": "IN",
        "device.id": f"dev-{random.randint(10000, 99999)}",
        "user.id": f"user-{random.randint(1, 10000)}",
        "app.version": random.choice(["2.4.0", "2.5.0", "2.5.1"]),
        "latency_ms": random.randint(10, 3000),
        "retry_count": random.randint(0, 3),
        "event_index": index,
    }


def create_string_value(value: str) -> AnyValue:
    """Create a protobuf AnyValue with string value."""
    any_value = AnyValue()
    any_value.string_value = str(value)
    return any_value


def create_key_value(key: str, value: str) -> KeyValue:
    """Create a protobuf KeyValue."""
    kv = KeyValue()
    kv.key = key
    kv.value.CopyFrom(create_string_value(value))
    return kv


def create_otlp_log_request_protobuf(events: list, resource_attrs: dict) -> bytes:
    """
    Create OTLP protobuf log request payload.
    Returns protobuf-encoded bytes.
    """
    if not PROTOBUF_AVAILABLE:
        raise ImportError("opentelemetry-proto is required. Install with: pip install opentelemetry-proto")
    
    now_nanos = int(time.time() * 1_000_000_000)
    
    # Create ExportLogsServiceRequest
    request = ExportLogsServiceRequest()
    
    # Create ResourceLogs
    resource_logs = ResourceLogs()
    
    # Create Resource with attributes
    resource = Resource()
    for key, value in resource_attrs.items():
        attr = create_key_value(key, value)
        resource.attributes.append(attr)
    resource_logs.resource.CopyFrom(resource)
    
    # Create ScopeLogs
    scope_logs = ScopeLogs()
    scope_logs.scope.name = "pulse-test"
    
    # Create LogRecords for each event
    for event in events:
        log_record = LogRecord()
        log_record.time_unix_nano = now_nanos
        log_record.observed_time_unix_nano = now_nanos
        
        # Set body
        log_record.body.CopyFrom(create_string_value(event["body"]))
        
        # Add attributes
        for key, value in event["attributes"].items():
            attr = create_key_value(key, str(value))
            log_record.attributes.append(attr)
        
        scope_logs.log_records.append(log_record)
    
    resource_logs.scope_logs.append(scope_logs)
    request.resource_logs.append(resource_logs)
    
    # Serialize to bytes
    return request.SerializeToString()


class VectorOTLPUser(FastHttpUser):
    """
    Locust user class that simulates sending OTLP log events to Vector.
    
    Each "user" represents a concurrent connection that sends requests.
    Uses FastHttpUser for better performance and Python 3.14 compatibility.
    """
    
    # Wait between 0.5 and 2 seconds between requests
    wait_time = between(0.5, 2.0)
    
    # Default endpoint path (will be combined with --host)
    endpoint = "/v1/logs"
    
    def on_start(self):
        """Called when a user starts. Initialize resource attributes."""
        self.resource_attrs = create_resource_attrs()
        self.event_counter = 0
    
    @task(3)  # Weight: 3 (more common)
    def send_single_event(self):
        """Send a single event (1 event per request)."""
        if not PROTOBUF_AVAILABLE:
            return
        
        self.event_counter += 1
        events = [{
            "body": random.choice(EVENT_NAMES),
            "attributes": create_log_attributes(self.event_counter)
        }]
        
        payload = create_otlp_log_request_protobuf(events, self.resource_attrs)
        compressed_payload = gzip.compress(payload)
        
        with self.client.post(
            self.endpoint,
            data=compressed_payload,
            headers={
                "Content-Type": "application/x-protobuf",
                "Content-Encoding": "gzip"
            },
            catch_response=True,
            name="OTLP Single Event",
            timeout=10
        ) as response:
            try:
                if response.status_code in (200, 201, 202):
                    response.success()
                else:
                    error_msg = response.text[:200] if hasattr(response, 'text') else str(response.status_code)
                    response.failure(f"Status {response.status_code}: {error_msg}")
            except Exception as e:
                # Connection errors, timeouts, etc.
                response.failure(f"Connection error: {str(e)[:100]}")
    
    @task(2)  # Weight: 2
    def send_batch_10_events(self):
        """Send a batch of 10 events."""
        if not PROTOBUF_AVAILABLE:
            return
        
        events = []
        for i in range(10):
            self.event_counter += 1
            events.append({
                "body": random.choice(EVENT_NAMES),
                "attributes": create_log_attributes(self.event_counter)
            })
        
        payload = create_otlp_log_request_protobuf(events, self.resource_attrs)
        compressed_payload = gzip.compress(payload)
        
        with self.client.post(
            self.endpoint,
            data=compressed_payload,
            headers={
                "Content-Type": "application/x-protobuf",
                "Content-Encoding": "gzip"
            },
            catch_response=True,
            name="OTLP Batch 10 Events",
            timeout=10
        ) as response:
            try:
                if response.status_code in (200, 201, 202):
                    response.success()
                else:
                    error_msg = response.text[:200] if hasattr(response, 'text') else str(response.status_code)
                    response.failure(f"Status {response.status_code}: {error_msg}")
            except Exception as e:
                response.failure(f"Connection error: {str(e)[:100]}")
    
    @task(1)  # Weight: 1 (less common)
    def send_batch_100_events(self):
        """Send a batch of 100 events."""
        if not PROTOBUF_AVAILABLE:
            return
        
        events = []
        for i in range(100):
            self.event_counter += 1
            events.append({
                "body": random.choice(EVENT_NAMES),
                "attributes": create_log_attributes(self.event_counter)
            })
        
        payload = create_otlp_log_request_protobuf(events, self.resource_attrs)
        compressed_payload = gzip.compress(payload)
        
        with self.client.post(
            self.endpoint,
            data=compressed_payload,
            headers={
                "Content-Type": "application/x-protobuf",
                "Content-Encoding": "gzip"
            },
            catch_response=True,
            name="OTLP Batch 100 Events",
            timeout=10
        ) as response:
            try:
                if response.status_code in (200, 201, 202):
                    response.success()
                else:
                    error_msg = response.text[:200] if hasattr(response, 'text') else str(response.status_code)
                    response.failure(f"Status {response.status_code}: {error_msg}")
            except Exception as e:
                response.failure(f"Connection error: {str(e)[:100]}")


# Optional: Custom test statistics
@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    """Called when the test starts."""
    print("\n" + "="*70)
    print("VECTOR OTLP LOAD TEST STARTING")
    print("="*70)
    print(f"Target: {environment.host}")
    print(f"Users: {environment.runner.target_user_count if hasattr(environment.runner, 'target_user_count') else 'N/A'}")
    
    if not PROTOBUF_AVAILABLE:
        print("\n" + "!"*70)
        print("ERROR: opentelemetry-proto is not installed!")
        print("Install with: pip install opentelemetry-proto")
        print("!"*70 + "\n")
    else:
        print("Protobuf encoding: ✓ Available")
    
    print("="*70 + "\n")


@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    """Called when the test stops."""
    print("\n" + "="*70)
    print("VECTOR OTLP LOAD TEST COMPLETED")
    print("="*70)
    stats = environment.stats
    print(f"Total Requests: {stats.total.num_requests}")
    print(f"Total Failures: {stats.total.num_failures}")
    print(f"Total RPS: {stats.total.total_rps:.2f}")
    print("="*70 + "\n")
    print("Check your S3 bucket for the parquet files:")
    print("  s3://puls-otel-config/year=YYYY/month=MM/day=DD/hour=HH/")