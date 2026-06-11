package com.imaging.jpeg;

import org.apache.commons.imaging.ImagingFormatException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JpegThumbnailCorruptionTest {

    static Stream<byte[]> provideCorruptedJpegData() throws IOException {
        return Stream.of(
                TestJpegBuilder.buildJpegWithCorruptedThumbnail_OffsetOutOfBounds(),
                TestJpegBuilder.buildJpegWithCorruptedThumbnail_LengthExceedsFile(),
                TestJpegBuilder.buildJpegWithCorruptedThumbnail_InternalTiffCorrupted()
        );
    }

    @ParameterizedTest
    @MethodSource("provideCorruptedJpegData")
    void testExtractThumbnailThrowsOnCorruptedData(byte[] jpegData) {
        ImagingFormatException exception = assertThrows(
                ImagingFormatException.class,
                () -> JpegThumbnailParser.extractThumbnail(jpegData)
        );
        assertTrue(exception.getMessage().contains("Thumbnail"));
    }
}
