#!/usr/bin/env python3
"""
Vector OTLP Load Test Script

Generates and sends OTLP log events to Vector with concurrent batches.

Usage:
    pip install opentelemetry-sdk opentelemetry-exporter-otlp-proto-http
    
    # Send 100k events with 10 concurrent connections
    python test_vector_pipeline.py --endpoint http://pulse-vector.delivr.local:4318/v1/logs \
        --count 100000 --batch-size 1000 --concurrency 10
"""

import time
import random
import argparse
import signal
import sys
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed

from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk._logs import LoggerProvider
from opentelemetry.sdk._logs.export import BatchLogRecordProcessor, SimpleLogRecordProcessor
from opentelemetry.exporter.otlp.proto.http._log_exporter import OTLPLogExporter
from opentelemetry import _logs


# Global flag for graceful shutdown
shutdown_requested = False

def signal_handler(signum, frame):
    global shutdown_requested
    print("\n\nShutdown requested...")
    shutdown_requested = True

signal.signal(signal.SIGINT, signal_handler)
signal.signal(signal.SIGTERM, signal_handler)


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


class BatchSender:
    """Handles sending a batch of events."""
    
    def __init__(self, endpoint: str, batch_size: int):
        self.endpoint = endpoint
        self.batch_size = batch_size
    
    def send_batch(self, batch_id: int, start_index: int, events_count: int) -> dict:
        """Send a batch of events. Returns result dict."""
        try:
            # Create exporter for this batch
            exporter = OTLPLogExporter(endpoint=self.endpoint, timeout=30)
            
            # Create provider with resource
            resource = Resource.create(create_resource_attrs())
            provider = LoggerProvider(resource=resource)
            
            # Use SimpleLogRecordProcessor for immediate export control
            # Then batch manually
            processor = BatchLogRecordProcessor(
                exporter,
                max_queue_size=events_count + 100,
                max_export_batch_size=events_count,  # Send all at once
                export_timeout_millis=30000,
                schedule_delay_millis=100,
            )
            provider.add_log_record_processor(processor)
            logger = provider.get_logger(f"batch-{batch_id}")
            
            # Generate and emit events
            for i in range(events_count):
                event_name = random.choice(EVENT_NAMES)
                logger.emit(
                    body=event_name,
                    attributes=create_log_attributes(start_index + i)
                )
            
            # Force flush to send immediately
            provider.force_flush(timeout_millis=30000)
            provider.shutdown()
            
            return {
                "batch_id": batch_id,
                "events": events_count,
                "success": True,
            }
            
        except Exception as e:
            return {
                "batch_id": batch_id,
                "events": events_count,
                "success": False,
                "error": str(e),
            }


def run_concurrent_test(endpoint: str, total_count: int, batch_size: int, concurrency: int):
    """Send events in concurrent batches."""
    global shutdown_requested
    
    # Calculate batches
    num_batches = (total_count + batch_size - 1) // batch_size
    
    print(f"\n{'='*70}")
    print("VECTOR CONCURRENT LOAD TEST")
    print(f"{'='*70}")
    print(f"Endpoint:        {endpoint}")
    print(f"Total events:    {total_count:,}")
    print(f"Batch size:      {batch_size:,} events/request")
    print(f"Concurrency:     {concurrency} parallel connections")
    print(f"Total batches:   {num_batches:,}")
    print(f"{'='*70}")
    print("Press Ctrl+C to stop\n")
    
    # Prepare batches
    batches = []
    current_index = 0
    for batch_id in range(num_batches):
        events_in_batch = min(batch_size, total_count - current_index)
        batches.append((batch_id, current_index, events_in_batch))
        current_index += events_in_batch
    
    # Stats
    sender = BatchSender(endpoint, batch_size)
    completed = 0
    successful = 0
    failed = 0
    total_events_sent = 0
    lock = threading.Lock()
    
    print(f"Sending {num_batches} batches with {concurrency} parallel connections...\n")
    start_time = time.perf_counter()
    last_report_time = start_time
    
    try:
        with ThreadPoolExecutor(max_workers=concurrency) as executor:
            # Submit all batches
            futures = {
                executor.submit(sender.send_batch, bid, start_idx, count): bid
                for bid, start_idx, count in batches
            }
            
            # Process results as they complete
            for future in as_completed(futures):
                if shutdown_requested:
                    break
                    
                result = future.result()
                
                with lock:
                    completed += 1
                    if result["success"]:
                        successful += 1
                        total_events_sent += result["events"]
                    else:
                        failed += 1
                        print(f"  Batch {result['batch_id']} failed: {result.get('error', 'Unknown')[:50]}")
                
                # Progress report
                current_time = time.perf_counter()
                if completed % max(1, num_batches // 10) == 0 or (current_time - last_report_time) >= 2.0:
                    elapsed = current_time - start_time
                    rate = total_events_sent / elapsed if elapsed > 0 else 0
                    pct = (completed / num_batches) * 100
                    print(f"  Progress: {completed}/{num_batches} batches ({pct:.0f}%) "
                          f"- {total_events_sent:,} events - {rate:,.0f} events/sec")
                    last_report_time = current_time
                    
    except Exception as e:
        print(f"\nError: {e}")
    
    end_time = time.perf_counter()
    duration = end_time - start_time
    throughput = total_events_sent / duration if duration > 0 else 0
    
    # Summary
    print(f"\n{'='*70}")
    print("TEST RESULTS")
    print(f"{'='*70}")
    print(f"Events sent:       {total_events_sent:,}")
    print(f"Batches completed: {successful:,} successful, {failed} failed")
    print(f"Duration:          {duration:.2f} seconds")
    print(f"Throughput:        {throughput:,.0f} events/second")
    print(f"Requests/sec:      {successful / duration:.1f}")
    print(f"{'='*70}\n")
    
    print("Check your S3 bucket for the parquet files:")
    print("  s3://puls-otel-config/year=YYYY/month=MM/day=DD/hour=HH/")


def check_connectivity(endpoint: str) -> bool:
    """Check if endpoint is reachable."""
    import socket
    from urllib.parse import urlparse
    
    parsed = urlparse(endpoint)
    host = parsed.hostname or 'localhost'
    port = parsed.port or 4318
    
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(5)
        result = sock.connect_ex((host, port))
        sock.close()
        return result == 0
    except Exception:
        return False


def main():
    parser = argparse.ArgumentParser(
        description="Generate and send test OTLP events to Vector with concurrent batches",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Quick test: 1000 events, 5 concurrent
  python test_vector_pipeline.py --count 1000 --batch-size 100 --concurrency 5
  
  # Standard: 100K events, 10 concurrent connections
  python test_vector_pipeline.py --count 100000 --batch-size 1000 --concurrency 10
  
  # High load: 1M events, 50 concurrent
  python test_vector_pipeline.py --count 1000000 --batch-size 1000 --concurrency 50

  # Skip connectivity check
  python test_vector_pipeline.py --count 1000 --skip-check
        """
    )
    parser.add_argument(
        "--endpoint", 
        default="http://pulse-vector.delivr.local:4318/v1/logs",
        help="OTLP HTTP endpoint"
    )
    parser.add_argument(
        "--count", 
        type=int, 
        default=100000,
        help="Total number of events to send (default: 100000)"
    )
    parser.add_argument(
        "--batch-size", 
        type=int, 
        default=1000,
        help="Events per batch/request (default: 1000)"
    )
    parser.add_argument(
        "--concurrency", 
        type=int, 
        default=10,
        help="Number of concurrent connections (default: 10)"
    )
    parser.add_argument(
        "--skip-check",
        action="store_true",
        help="Skip connectivity check"
    )
    
    args = parser.parse_args()
    
    if args.count <= 0 or args.batch_size <= 0 or args.concurrency <= 0:
        print("Error: count, batch-size, and concurrency must be positive")
        return
    
    # Connectivity check
    if not args.skip_check:
        print(f"Testing connectivity to {args.endpoint}...")
        if not check_connectivity(args.endpoint):
            print(f"ERROR: Cannot connect to endpoint")
            print("Use --skip-check to bypass this check")
            sys.exit(1)
        print("Connected successfully!\n")
    
    run_concurrent_test(
        endpoint=args.endpoint,
        total_count=args.count,
        batch_size=args.batch_size,
        concurrency=args.concurrency,
    )


if __name__ == "__main__":
    main()
