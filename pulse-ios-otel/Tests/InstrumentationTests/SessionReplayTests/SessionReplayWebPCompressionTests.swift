/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import XCTest
@testable import PulseKit

#if os(iOS) || os(tvOS)

class SessionReplayWebPCompressionTests: XCTestCase {
    
    // MARK: - Helper Methods
    
    /// Create a test UIImage with specified size and color
    private func createTestImage(width: Int, height: Int, color: UIColor = .red) -> UIImage {
        let size = CGSize(width: width, height: height)
        UIGraphicsBeginImageContextWithOptions(size, false, 1.0)
        color.setFill()
        UIBezierPath(rect: CGRect(origin: .zero, size: size)).fill()
        let image = UIGraphicsGetImageFromCurrentImageContext() ?? UIImage()
        UIGraphicsEndImageContext()
        return image
    }
    
    /// Create a test image with gradient pattern
    private func createGradientImage(width: Int, height: Int) -> UIImage {
        let size = CGSize(width: width, height: height)
        UIGraphicsBeginImageContextWithOptions(size, false, 1.0)
        
        guard let context = UIGraphicsGetCurrentContext() else {
            return UIImage()
        }
        
        // Create a gradient from red to blue
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let colors: [CGFloat] = [
            1, 0, 0, 1,  // Red
            0, 0, 1, 1   // Blue
        ]
        guard let gradient = CGGradient(colorSpace: colorSpace, colorComponents: colors, locations: nil, count: 2) else {
            return UIImage()
        }
        
        context.drawLinearGradient(
            gradient,
            start: CGPoint(x: 0, y: 0),
            end: CGPoint(x: CGFloat(width), y: CGFloat(height)),
            options: []
        )
        
        let image = UIGraphicsGetImageFromCurrentImageContext() ?? UIImage()
        UIGraphicsEndImageContext()
        return image
    }
    
    /// Create a test image with text
    private func createTextImage(width: Int, height: Int, text: String) -> UIImage {
        let size = CGSize(width: width, height: height)
        UIGraphicsBeginImageContextWithOptions(size, false, 1.0)
        
        UIColor.white.setFill()
        UIBezierPath(rect: CGRect(origin: .zero, size: size)).fill()
        
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = .center
        
        let attributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: 20),
            .foregroundColor: UIColor.black,
            .paragraphStyle: paragraph
        ]
        
        text.draw(in: CGRect(origin: .zero, size: size), withAttributes: attributes)
        
        let image = UIGraphicsGetImageFromCurrentImageContext() ?? UIImage()
        UIGraphicsEndImageContext()
        return image
    }
    
    // MARK: - Basic WebP Compression Tests
    
    /// Test that WebP compression produces valid data
    func testWebPCompressionProducesValidData() {
        let image = createTestImage(width: 100, height: 100)
        
        let result = SessionReplayCompressor.compress(image: image, quality: 0.8)
        
        XCTAssertNotNil(result, "Compression should return non-nil result")
        XCTAssertGreaterThan(result!.data.count, 0, "Compressed data should not be empty")
        XCTAssertEqual(result!.format, .webp, "Format should be WebP")
    }
    
    /// Test that WebP compression respects quality parameter
    func testWebPQualityAffectsFileSize() {
        let image = createGradientImage(width: 200, height: 200)
        
        let highQualityResult = SessionReplayCompressor.compress(image: image, quality: 0.95)
        let lowQualityResult = SessionReplayCompressor.compress(image: image, quality: 0.3)
        
        XCTAssertNotNil(highQualityResult, "High quality compression should succeed")
        XCTAssertNotNil(lowQualityResult, "Low quality compression should succeed")
        
        // Lower quality should generally produce smaller files
        XCTAssertLessThan(
            lowQualityResult!.data.count,
            highQualityResult!.data.count,
            "Lower quality should produce smaller file"
        )
    }
    
    /// Test compression with various image sizes
    func testWebPCompressionWithVariousSizes() {
        let sizes: [(width: Int, height: Int)] = [
            (50, 50),
            (100, 100),
            (200, 200),
            (500, 500),
            (1000, 1000)
        ]
        
        for size in sizes {
            let image = createTestImage(width: size.width, height: size.height)
            
            let result = SessionReplayCompressor.compress(image: image, quality: 0.75)
            
            XCTAssertNotNil(
                result,
                "Compression should succeed for size \(size.width)x\(size.height)"
            )
            XCTAssertGreaterThan(
                result!.data.count,
                0,
                "Compressed data should not be empty for size \(size.width)x\(size.height)"
            )
        }
    }
    
    /// Test WebP compression with rectangular images
    func testWebPCompressionWithRectangularImages() {
        let rectangularSizes: [(width: Int, height: Int)] = [
            (100, 50),
            (200, 100),
            (50, 100),
            (100, 200)
        ]
        
        for size in rectangularSizes {
            let image = createTestImage(width: size.width, height: size.height)
            
            let result = SessionReplayCompressor.compress(image: image, quality: 0.8)
            
            XCTAssertNotNil(
                result,
                "Compression should succeed for rectangular size \(size.width)x\(size.height)"
            )
        }
    }
    
    // MARK: - Quality Tests
    
    /// Test compression quality extremes
    func testWebPQualityExtremes() {
        // Use a gradient image instead of a solid color to guarantee variation in algorithm compression
        let image = createGradientImage(width: 150, height: 150)
        
        // Test minimum quality (0.0)
        let minQualityResult = SessionReplayCompressor.compress(image: image, quality: 0.0)
        XCTAssertNotNil(minQualityResult, "Compression with quality 0.0 should succeed")
        
        // Test maximum quality (1.0)
        let maxQualityResult = SessionReplayCompressor.compress(image: image, quality: 1.0)
        XCTAssertNotNil(maxQualityResult, "Compression with quality 1.0 should succeed")
        
        // Max quality should be larger than min quality
        XCTAssertGreaterThan(
            maxQualityResult!.data.count,
            minQualityResult!.data.count,
            "Higher quality should produce larger file"
        )
    }
    
    /// Test various quality levels
    func testWebPVariousQualityLevels() {
        let image = createGradientImage(width: 200, height: 200)
        let qualities: [CGFloat] = [0.1, 0.3, 0.5, 0.7, 0.9]
        
        var previousSize: Int? = nil
        
        for quality in qualities {
            let result = SessionReplayCompressor.compress(image: image, quality: quality)
            
            XCTAssertNotNil(result, "Compression with quality \(quality) should succeed")
            
            if let prevSize = previousSize {
                // Higher quality should generally produce larger or equal file sizes
                XCTAssertGreaterThanOrEqual(
                    result!.data.count,
                    prevSize,
                    "Quality \(quality) should produce larger or equal file compared to previous"
                )
            }
            previousSize = result?.data.count
        }
    }
    
    // MARK: - Image Content Tests
    
    /// Test compression with different image contents
    func testWebPCompressionWithDifferentContents() {
        let solidColor = createTestImage(width: 200, height: 200, color: .green)
        let gradient = createGradientImage(width: 200, height: 200)
        let text = createTextImage(width: 200, height: 200, text: "Test Image")
        
        let solidResult = SessionReplayCompressor.compress(image: solidColor, quality: 0.8)
        let gradientResult = SessionReplayCompressor.compress(image: gradient, quality: 0.8)
        let textResult = SessionReplayCompressor.compress(image: text, quality: 0.8)
        
        XCTAssertNotNil(solidResult, "Solid color compression should succeed")
        XCTAssertNotNil(gradientResult, "Gradient compression should succeed")
        XCTAssertNotNil(textResult, "Text image compression should succeed")
        
        // All should produce valid WebP data
        XCTAssertEqual(solidResult!.format, .webp)
        XCTAssertEqual(gradientResult!.format, .webp)
        XCTAssertEqual(textResult!.format, .webp)
        
        // Solid color should be smallest (most compressible)
        XCTAssertLessThan(solidResult!.data.count, gradientResult!.data.count)
        XCTAssertLessThan(solidResult!.data.count, textResult!.data.count)
    }
    
    // MARK: - Fallback Tests
    
    /// Test fallback to JPEG when WebP is not available
    func testCompressionFallbackToJPEG() {
        let image = createTestImage(width: 100, height: 100)
        
        // compress() tries WebP first, then falls back to JPEG
        let result = SessionReplayCompressor.compress(image: image, quality: 0.8)
        
        XCTAssertNotNil(result, "Compression should return a result")
        // Could be .webp or .jpeg depending on availability
        XCTAssertTrue(
            result!.format == .webp || result!.format == .jpeg,
            "Format should be either WebP or JPEG"
        )
    }
    
    // MARK: - Performance Tests
    
    /// Test compression performance doesn't timeout
    func testWebPCompressionPerformance() {
        let image = createTestImage(width: 500, height: 500)
        
        self.measure {
            _ = SessionReplayCompressor.compress(image: image, quality: 0.8)
        }
    }
    
    /// Test compression with large images
    func testWebPCompressionLargeImage() {
        let largeImage = createTestImage(width: 1000, height: 1000)
        
        let result = SessionReplayCompressor.compress(image: largeImage, quality: 0.75)
        
        XCTAssertNotNil(result, "Large image compression should succeed")
        XCTAssertGreaterThan(result!.data.count, 0, "Compressed large image should have data")
    }
    
    // MARK: - Data Validity Tests
    
    /// Test that compressed data is actually different from original
    func testCompressedDataDifference() {
        let image = createGradientImage(width: 200, height: 200)
        
        guard let originalData = image.pngData() else {
            XCTFail("Could not get PNG data from image")
            return
        }
        
        let result = SessionReplayCompressor.compress(image: image, quality: 0.8)
        
        XCTAssertNotNil(result, "Compression should succeed")
        XCTAssertNotEqual(
            result!.data,
            originalData,
            "Compressed WebP should differ from original PNG"
        )
    }
    
    /// Test compression data contains WebP file signature
    func testWebPDataHasValidSignature() {
        let image = createTestImage(width: 100, height: 100)
        
        let result = SessionReplayCompressor.compress(image: image, quality: 0.8)
        
        XCTAssertNotNil(result, "Compression should succeed")
        
        if result!.format == .webp {
            // WebP files should start with specific bytes: RIFF....WEBP
            let data = result!.data
            XCTAssertGreaterThanOrEqual(data.count, 12, "WebP file should be at least 12 bytes")
            
            // Check for RIFF signature at start
            let riffSignature: [UInt8] = [0x52, 0x49, 0x46, 0x46]  // "RIFF"
            let riffBytes = data.prefix(4)
            XCTAssertEqual(
                Array(riffBytes),
                riffSignature,
                "WebP data should start with RIFF signature"
            )
            
            // Check for WEBP signature
            let webpSignature: [UInt8] = [0x57, 0x45, 0x42, 0x50]  // "WEBP"
            let webpBytes = data.subdata(in: 8..<12)
            XCTAssertEqual(
                Array(webpBytes),
                webpSignature,
                "WebP data should contain WEBP signature at offset 8"
            )
        }
    }
    
    // MARK: - Edge Case Tests
    
    /// Test compression with minimal size image
    func testWebPCompressionMinimalSize() {
        let tinyImage = createTestImage(width: 1, height: 1)
        
        let result = SessionReplayCompressor.compress(image: tinyImage, quality: 0.8)
        
        XCTAssertNotNil(result, "Compression should handle minimal size image")
    }
    
    /// Test compression consistency
    func testWebPCompressionConsistency() {
        let image = createTestImage(width: 150, height: 150)
        
        let result1 = SessionReplayCompressor.compress(image: image, quality: 0.8)
        let result2 = SessionReplayCompressor.compress(image: image, quality: 0.8)
        
        XCTAssertNotNil(result1, "First compression should succeed")
        XCTAssertNotNil(result2, "Second compression should succeed")
        
        // Results should be consistent (same image, same quality)
        XCTAssertEqual(result1!.data, result2!.data, "Compression should be consistent")
    }
}

#endif
