package org.dreamhorizon.pulseserver.errorgrouping.service

import io.reactivex.rxjava3.core.Single
import io.vertx.core.Vertx
import org.dreamhorizon.pulseserver.errorgrouping.model.SymbolFileType
import org.dreamhorizon.pulseserver.errorgrouping.model.UploadMetadata
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class NdkSymbolsCacheTest {

    @Mock
    private lateinit var symbolFileService: SymbolFileService

    private lateinit var vertx: Vertx
    private lateinit var cache: NdkSymbolsCache

    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()
        cache = NdkSymbolsCache(vertx, symbolFileService)
    }

    @AfterEach
    fun tearDown() {
        vertx.close()
    }

    @Test
    fun getNdkSymbols_returnsBytesWhenPresent() {
        val payload = byteArrayOf(1, 2, 3)
        `when`(symbolFileService.readFileAsBytes(any(UploadMetadata::class.java)))
            .thenReturn(Single.just(payload))

        val got = cache.getNdkSymbols(key()).blockingGet()

        assertTrue(got.isPresent)
        assertArrayEquals(payload, got.get())
    }

    @Test
    fun getNdkSymbols_emptyBytes_triggersNegativeCacheAndReturnsEmpty() {
        `when`(symbolFileService.readFileAsBytes(any(UploadMetadata::class.java)))
            .thenReturn(Single.just(ByteArray(0)))

        val first = cache.getNdkSymbols(key()).blockingGet()
        assertFalse(first.isPresent)

        val second = cache.getNdkSymbols(key()).blockingGet()
        assertFalse(second.isPresent)
        verify(symbolFileService, times(1)).readFileAsBytes(any(UploadMetadata::class.java))
    }

    @Test
    fun getNdkSymbols_error_triggersNegativeCacheAndReturnsEmpty() {
        `when`(symbolFileService.readFileAsBytes(any(UploadMetadata::class.java)))
            .thenReturn(Single.error(RuntimeException("s3 down")))

        val got = cache.getNdkSymbols(key()).blockingGet()
        assertFalse(got.isPresent)
    }

    private fun key(): UploadMetadata =
        UploadMetadata.builder()
            .projectId("proj")
            .platform("android")
            .appVersion("1.0")
            .versionCode("1")
            .type(SymbolFileType.NDK)
            .build()
}
