/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.imaging.jpeg;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.commons.imaging.ImagingFormatException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.imaging.jpeg.TestJpegBuilder.CorruptionMode;

class JpegThumbnailCorruptionTest {

    @ParameterizedTest
    @MethodSource("corruptionModes")
    void extractThumbnailFailsWithImagingFormatExceptionContainingThumbnail(final CorruptionMode mode) {
        final byte[] jpeg = TestJpegBuilder.buildJpeg(mode);

        final ImagingFormatException ex = assertThrows(ImagingFormatException.class,
                () -> JpegThumbnailParser.extractThumbnail(jpeg));

        if (ex.getMessage() == null || !ex.getMessage().contains("Thumbnail")) {
            throw new AssertionError(
                    "Expected ImagingFormatException message to contain 'Thumbnail', but was: " + ex.getMessage());
        }
    }

    @ParameterizedTest
    @MethodSource("corruptionModes")
    void inputIsWellFormedJpeg(final CorruptionMode mode) {
        // Sanity check: TestJpegBuilder always produces a JPEG starting with SOI.
        final byte[] jpeg = TestJpegBuilder.buildJpeg(mode);
        assertArrayEquals(new byte[] { (byte) 0xFF, (byte) 0xD8 }, new byte[] { jpeg[0], jpeg[1] });
    }

    static CorruptionMode[] corruptionModes() {
        return new CorruptionMode[] {
                CorruptionMode.OFFSET_OUT_OF_BOUNDS,
                CorruptionMode.LENGTH_EXCEEDS_FILE,
                CorruptionMode.NEXT_IFD_INVALID
        };
    }
}
