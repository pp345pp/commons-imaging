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

package org.apache.commons.imaging.formats.jpeg;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.apache.commons.imaging.ImagingFormatException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JpegThumbnailCorruptionTest {

    static Stream<Arguments> corruptedJpegProvider() {
        return Stream.of(
                Arguments.of("thumbnail offset beyond end", TestJpegBuilder.buildWithThumbnailOffsetBeyondEnd()),
                Arguments.of("thumbnail length exceeds remaining size", TestJpegBuilder.buildWithThumbnailLengthExceeds()),
                Arguments.of("corrupted Next IFD offset", TestJpegBuilder.buildWithCorruptedNextIfd()));
    }

    @ParameterizedTest
    @MethodSource("corruptedJpegProvider")
    void testCorruptedThumbnail(final String description, final byte[] jpegBytes) {
        final ImagingFormatException exception = assertThrows(ImagingFormatException.class,
                () -> JpegThumbnailParser.extractThumbnail(jpegBytes));
        assertTrue(exception.getMessage().contains("Thumbnail"),
                () -> "Exception message must contain 'Thumbnail': " + exception.getMessage());
    }
}