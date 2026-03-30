//! Payload decompression and decoding logic
//!
//! This module handles decompression of HTTP request payloads using
//! GZIP compression format. Adapted from PostHog's capture service
//! decompression pipeline.

use std::io::prelude::*;

use bytes::{Buf, Bytes};
use flate2::read::GzDecoder;
use tracing::{debug, error, instrument, warn};

use crate::api::CaptureError;

pub static GZIP_MAGIC_NUMBERS: [u8; 3] = [0x1f, 0x8b, 0x08];

/// Decompresses and decodes a payload based on content detection.
///
/// # Arguments
/// * `bytes` - Raw compressed/encoded bytes from the HTTP request
/// * `limit` - Maximum allowed size for decompressed payload (in bytes)
///
/// # Returns
/// Decompressed UTF-8 string payload ready for JSON deserialization
#[instrument(skip_all, fields(payload_len = bytes.len()))]
pub fn decompress_payload(bytes: Bytes, limit: usize) -> Result<String, CaptureError> {
    metrics::histogram!("capture_raw_payload_size").record(bytes.len() as f64);

    debug!(
        payload_len = bytes.len(),
        "decompress_payload: decoding payload"
    );

    let payload = if bytes.starts_with(&GZIP_MAGIC_NUMBERS) {
        let len = bytes.len();
        debug!(
            payload_len = len,
            "decompress_payload: matched GZIP compression"
        );

        let mut zipstream = GzDecoder::new(bytes.reader());
        let mut chunk = [0; 8192];
        let mut buf = Vec::new();
        let mut total_read = 0;

        loop {
            let got = match zipstream.read(&mut chunk) {
                Ok(got) => got,
                Err(e) => {
                    error!(
                        "decompress_payload: failed to read GZIP chunk from stream: {}",
                        e
                    );
                    return Err(CaptureError::DecompressionError(String::from(
                        "invalid GZIP data",
                    )));
                }
            };
            if got == 0 {
                break;
            }

            // Check size BEFORE allocation to prevent memory spikes
            if total_read + got > limit {
                error!(
                    decompressed_size = total_read + got,
                    compressed_size = len,
                    limit = limit,
                    "decompress_payload: GZIP decompression would exceed size limit"
                );

                return Err(CaptureError::PayloadTooBig(format!(
                    "Decompressed payload would exceed {} bytes (got {} bytes)",
                    limit,
                    total_read + got
                )));
            }

            buf.extend_from_slice(&chunk[..got]);
            total_read += got;
        }

        // Warn on potential GZIP bombs
        if len > 0 {
            let ratio = total_read as f64 / len as f64;
            if ratio > 20.0 {
                warn!(
                    compressed_size = len,
                    decompressed_size = total_read,
                    ratio = ratio,
                    "High GZIP compression ratio detected - potential GZIP bomb"
                );
            }
        }

        match String::from_utf8(buf) {
            Ok(s) => s,
            Err(e) => {
                error!("decompress_payload: failed to decode gzip: {}", e);
                return Err(CaptureError::DecompressionError(String::from(
                    "invalid gzip data",
                )));
            }
        }
    } else {
        debug!(
            payload_len = bytes.len(),
            "decompress_payload: no compression detected, assuming plain text"
        );

        let s = String::from_utf8(bytes.into()).map_err(|e| {
            error!(
                valid_up_to = &e.utf8_error().valid_up_to(),
                "decompress_payload: failed to convert request payload to UTF8: {}", e
            );
            CaptureError::InvalidPayload(String::from("invalid UTF8 in request payload"))
        })?;
        if s.len() > limit {
            error!(
                payload_size = s.len(),
                limit = limit,
                "decompress_payload: request size limit reached"
            );
            return Err(CaptureError::PayloadTooBig(format!(
                "Uncompressed payload size limit {} exceeded: {}",
                limit,
                s.len(),
            )));
        }
        s
    };

    metrics::histogram!("capture_full_payload_size").record(payload.len() as f64);

    debug!(
        decompressed_len = payload.len(),
        "decompress_payload: payload extracted"
    );

    Ok(payload)
}
