#!/usr/bin/env python3
"""
Vector OTLP High-Performance Load Test Script (Async)

Uses async/await for high concurrency without thread overhead.
Can handle 100k+ requests efficiently with limited concurrent connections.

Usage:
    pip install aiohttp opentelemetry-proto
    
    # Send 100k requests with 200 concurrent connections
    python test_vector_pipeline_async.py --endpoint http://pulse-vector.delivr.local:4318/v1/logs \
        --count 100000 --batch-size 1 --concurrency 200
"""

import time
import random
import argparse
import signal
import sys
import asyncio
import json
from typing import List, Dict, Any

try:
    import aiohttp
except ImportError:
    print("ERROR: aiohttp not installed. Run: pip install aiohttp")
    sys.exit(1)


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


def create_resource_attrs() -> Dict[str, str]:
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


def create_log_attributes(index: int) -> Dict[str, Any]:
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


def create_otlp_log_request(events: List[Dict[str, Any]], resource_attrs: Dict[str, str]) -> Dict[str, Any]:
    """
    Create OTLP JSON log request payload.
    Simplified version matching OTLP HTTP JSON format.
    """
    now_nanos = int(time.time() * 1_000_000_000)
    
    resource_spans = []
    for event in events:
        log_record = {
            "timeUnixNano": str(now_nanos),
            "observedTimeUnixNano": str(now_nanos),
            "body": {
                "stringValue": event["body"]
            },
            "attributes": [
                {"key": k, "value": {"stringValue": str(v)}}
                for k, v in event["attributes"].items()
            ]
        }
        
        resource_spans.append({
            "resource": {
                "attributes": [
                    {"key": k, "value": {"stringValue": str(v)}}
                    for k, v in resource_attrs.items()
                ]
            },
            "scopeLogs": [{
                "scope": {"name": "pulse-test"},
                "logRecords": [log_record]
            }]
        })
    
    return {
        "resourceLogs": resource_spans
    }


async def send_batch(
    session: aiohttp.ClientSession,
    endpoint: str,
    batch_id: int,
    events: List[Dict[str, Any]],
    resource_attrs: Dict[str, str],
    semaphore: asyncio.Semaphore
) -> Dict[str, Any]:
    """Send a batch of events asynchronously."""
    async with semaphore:  # Limit concurrent requests
        try:
            payload = create_otlp_log_request(events, resource_attrs)
            
            async with session.post(
                endpoint,
                json=payload,
                timeout=aiohttp.ClientTimeout(total=30)
            ) as response:
                if response.status in (200, 201, 202):
                    return {
                        "batch_id": batch_id,
                        "events": len(events),
                        "success": True,
                        "status": response.status
                    }
                else:
                    text = await response.text()
                    return {
                        "batch_id": batch_id,
                        "events": len(events),
                        "success": False,
                        "status": response.status,
                        "error": text[:100]
                    }
        except asyncio.TimeoutError:
            return {
                "batch_id": batch_id,
                "events": len(events),
                "success": False,
                "error": "Timeout"
            }
        except Exception as e:
            return {
                "batch_id": batch_id,
                "events": len(events),
                "success": False,
                "error": str(e)[:100]
            }


async def run_async_test(
    endpoint: str,
    total_count: int,
    batch_size: int,
    concurrency: int
):
    """Run async load test."""
    global shutdown_requested
    
    num_batches = (total_count + batch_size - 1) // batch_size
    
    print(f"\n{'='*70}")
    print("VECTOR ASYNC HIGH-PERFORMANCE LOAD TEST")
    print(f"{'='*70}")
    print(f"Endpoint:        {endpoint}")
    print(f"Total events:     {total_count:,}")
    print(f"Batch size:       {batch_size:,} events/request")
    print(f"Concurrency:      {concurrency} concurrent connections")
    print(f"Total requests:   {num_batches:,}")
    print(f"{'='*70}")
    print("Press Ctrl+C to stop\n")
    
    # Prepare batches
    batches = []
    current_index = 0
    for batch_id in range(num_batches):
        events_in_batch = min(batch_size, total_count - current_index)
        events = []
        for i in range(events_in_batch):
            events.append({
                "body": random.choice(EVENT_NAMES),
                "attributes": create_log_attributes(current_index + i)
            })
        batches.append((batch_id, events))
        current_index += events_in_batch
    
    # Stats
    completed = 0
    successful = 0
    failed = 0
    total_events_sent = 0
    start_time = time.perf_counter()
    last_report_time = start_time
    
    # Create semaphore to limit concurrent connections
    semaphore = asyncio.Semaphore(concurrency)
    
    # Create aiohttp session with connection pooling
    connector = aiohttp.TCPConnector(
        limit=concurrency * 2,  # Connection pool size
        limit_per_host=concurrency,
        ttl_dns_cache=300,
        force_close=False,
    )
    
    async with aiohttp.ClientSession(connector=connector) as session:
        print(f"Sending {num_batches:,} requests with {concurrency} concurrent connections...\n")
        
        # Create tasks for all batches
        tasks = []
        resource_attrs = create_resource_attrs()
        
        for batch_id, events in batches:
            if shutdown_requested:
                break
            task = send_batch(session, endpoint, batch_id, events, resource_attrs, semaphore)
            tasks.append(task)
        
        # Process results as they complete
        for coro in asyncio.as_completed(tasks):
            if shutdown_requested:
                break
                
            result = await coro
            completed += 1
            
            if result["success"]:
                successful += 1
                total_events_sent += result["events"]
            else:
                failed += 1
                if failed <= 10:  # Only show first 10 errors
                    print(f"  Batch {result['batch_id']} failed: {result.get('error', 'Unknown')[:50]}")
            
            # Progress report
            current_time = time.perf_counter()
            if completed % max(1, num_batches // 20) == 0 or (current_time - last_report_time) >= 1.0:
                elapsed = current_time - start_time
                rate = total_events_sent / elapsed if elapsed > 0 else 0
                req_rate = successful / elapsed if elapsed > 0 else 0
                pct = (completed / num_batches) * 100
                print(f"  Progress: {completed:,}/{num_batches:,} ({pct:.0f}%) "
                      f"- {total_events_sent:,} events - {rate:,.0f} events/sec - {req_rate:.0f} req/sec")
                last_report_time = current_time
    
    end_time = time.perf_counter()
    duration = end_time - start_time
    throughput = total_events_sent / duration if duration > 0 else 0
    req_throughput = successful / duration if duration > 0 else 0
    
    # Summary
    print(f"\n{'='*70}")
    print("TEST RESULTS")
    print(f"{'='*70}")
    print(f"Events sent:       {total_events_sent:,}")
    print(f"Requests:          {successful:,} successful, {failed} failed")
    print(f"Duration:          {duration:.2f} seconds")
    print(f"Event throughput:  {throughput:,.0f} events/second")
    print(f"Request throughput: {req_throughput:,.0f} requests/second")
    print(f"{'='*70}\n")
    
    print("Check your S3 bucket for the parquet files:")
    print("  s3://puls-otel-config/year=YYYY/month=MM/day=DD/hour=HH/")


def main():
    parser = argparse.ArgumentParser(
        description="High-performance async load test for Vector",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Quick test: 1000 requests, 50 concurrent
  python test_vector_pipeline_async.py --count 1000 --batch-size 1 --concurrency 50
  
  # High load: 100k requests, 200 concurrent
  python test_vector_pipeline_async.py --count 100000 --batch-size 1 --concurrency 200
  
  # Maximum throughput: 100k requests, 500 concurrent
  python test_vector_pipeline_async.py --count 100000 --batch-size 1 --concurrency 500

Note: For 100k parallel requests, use --batch-size 1 and --concurrency 200-500.
Higher concurrency may hit OS/network limits.
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
        help="Total number of events/requests (default: 100000)"
    )
    parser.add_argument(
        "--batch-size", 
        type=int, 
        default=1,
        help="Events per request (default: 1 for max requests)"
    )
    parser.add_argument(
        "--concurrency", 
        type=int, 
        default=200,
        help="Concurrent connections (default: 200, max recommended: 500)"
    )
    
    args = parser.parse_args()
    
    if args.count <= 0 or args.batch_size <= 0 or args.concurrency <= 0:
        print("Error: count, batch-size, and concurrency must be positive")
        return
    
    if args.concurrency > 1000:
        print(f"WARNING: Concurrency {args.concurrency} is very high.")
        print("Recommended max: 500. You may hit OS/network limits.")
        response = input("Continue anyway? [y/N]: ")
        if response.lower() != 'y':
            return
    
    # Run async test
    try:
        asyncio.run(run_async_test(
            endpoint=args.endpoint,
            total_count=args.count,
            batch_size=args.batch_size,
            concurrency=args.concurrency,
        ))
    except KeyboardInterrupt:
        print("\nTest interrupted by user")
    except Exception as e:
        print(f"\nError: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    main()
