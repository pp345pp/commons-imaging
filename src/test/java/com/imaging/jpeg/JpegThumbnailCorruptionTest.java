package com.imaging.jpeg;

import org.apache.commons.imaging.ImagingFormatException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JpegThumbnailCorruptionTest {

    static Stream<byte[]> corruptedJpegProvider() {
        try {
            return Stream.of(
                    TestJpegBuilder.buildCorruptedJpeg(TestJpegBuilder.CorruptionType.OFFSET_OUT_OF_BOUNDS),
                    TestJpegBuilder.buildCorruptedJpeg(TestJpegBuilder.CorruptionType.LENGTH_OUT_OF_BOUNDS),
                    TestJpegBuilder.buildCorruptedJpeg(TestJpegBuilder.CorruptionType.CORRUPT_TIFF_STRUCTURE)
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @ParameterizedTest
    @MethodSource("corruptedJpegProvider")
    public void testCorruptedThumbnail(byte[] jpegData) {
        ImagingFormatException exception = assertThrows(
                ImagingFormatException.class,
                () -> JpegThumbnailParser.extractThumbnail(jpegData)
        );
        assertTrue(exception.getMessage().contains("Thumbnail"),
                "Exception message should contain 'Thumbnail'");
    }
}
