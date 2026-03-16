use metrics_exporter_prometheus::{Matcher, PrometheusBuilder, PrometheusHandle};

const EXPONENTIAL_SECONDS: &[f64] = &[
    0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0, 30.0, 60.0,
];

const PAYLOAD_SIZES: &[f64] = &[
    100.0,
    1_000.0,
    10_000.0,
    100_000.0,
    500_000.0,
    1_000_000.0,
    5_000_000.0,
    10_000_000.0,
    25_000_000.0,
];

const BATCH_SIZES: &[f64] = &[1.0, 2.0, 5.0, 10.0, 25.0, 50.0, 100.0, 250.0, 500.0];

pub fn setup_metrics_recorder() -> PrometheusHandle {
    PrometheusBuilder::new()
        .set_buckets_for_metric(
            Matcher::Full("http_requests_duration_seconds".to_string()),
            EXPONENTIAL_SECONDS,
        )
        .unwrap()
        .set_buckets_for_metric(
            Matcher::Suffix("_batch_size".to_string()),
            BATCH_SIZES,
        )
        .unwrap()
        .set_buckets_for_metric(
            Matcher::Full("capture_full_payload_size".to_string()),
            PAYLOAD_SIZES,
        )
        .unwrap()
        .set_buckets_for_metric(
            Matcher::Full("capture_raw_payload_size".to_string()),
            PAYLOAD_SIZES,
        )
        .unwrap()
        .install_recorder()
        .unwrap()
}
