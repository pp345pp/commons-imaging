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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

final class TestJpegBuilder {

    enum ThumbnailCorruptionMode {
        OFFSET_OUTSIDE_FILE,
        LENGTH_EXCEEDS_REMAINING_SIZE,
        INVALID_INTERNAL_TIFF
    }

    private static final int JPEG_INTERCHANGE_FORMAT_TAG = 0x0201;
    private static final int JPEG_INTERCHANGE_FORMAT_LENGTH_TAG = 0x0202;
    private static final int SOI_MARKER = 0xffd8;
    private static final int TIFF_TYPE_LONG = 4;
    private static final int THUMBNAIL_IFD_OFFSET = 14;
    private static final int THUMBNAIL_DATA_OFFSET = 44;
    private static final byte[] EMBEDDED_THUMBNAIL = { (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9 };

    private TestJpegBuilder() {
    }

    static Stream<byte[]> corruptedThumbnailJpegs() {
        return Stream.of(
                buildCorruptedThumbnailJpeg(ThumbnailCorruptionMode.OFFSET_OUTSIDE_FILE),
                buildCorruptedThumbnailJpeg(ThumbnailCorruptionMode.LENGTH_EXCEEDS_REMAINING_SIZE),
                buildCorruptedThumbnailJpeg(ThumbnailCorruptionMode.INVALID_INTERNAL_TIFF));
    }

    static byte[] buildCorruptedThumbnailJpeg(final ThumbnailCorruptionMode corruptionMode) {
        final ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        writeMarker(jpeg, SOI_MARKER);
        writeSegment(jpeg, JpegConstants.JPEG_APP0_MARKER, buildApp0Segment());
        writeSegment(jpeg, JpegConstants.JPEG_APP1_MARKER, buildExifSegment(corruptionMode));
        writeSegment(jpeg, JpegConstants.DQT_MARKER, buildDqtSegment());
        writeSegment(jpeg, JpegConstants.SOF0_MARKER, buildSof0Segment());
        writeSegment(jpeg, JpegConstants.DHT_MARKER, buildDhtSegment());
        writeSegment(jpeg, JpegConstants.SOS_MARKER, buildSosSegment());
        jpeg.write(0x00);
        writeMarker(jpeg, JpegConstants.EOI_MARKER);
        return jpeg.toByteArray();
    }

    private static byte[] buildApp0Segment() {
        final ByteArrayOutputStream app0 = new ByteArrayOutputStream();
        app0.write("JFIF\u0000".getBytes(StandardCharsets.US_ASCII), 0, 5);
        writeUnsignedShort(app0, 0x0101);
        app0.write(0x00);
        writeUnsignedShort(app0, 0x0001);
        writeUnsignedShort(app0, 0x0001);
        app0.write(0x00);
        app0.write(0x00);
        return app0.toByteArray();
    }

    private static byte[] buildDhtSegment() {
        return new byte[] {
                0x00,
                0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00,
                0x10,
                0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00,
        };
    }

    private static byte[] buildDqtSegment() {
        final ByteArrayOutputStream dqt = new ByteArrayOutputStream();
        dqt.write(0x00);
        for (int i = 0; i < 64; i++) {
            dqt.write(0x01);
        }
        return dqt.toByteArray();
    }

    private static byte[] buildExifSegment(final ThumbnailCorruptionMode corruptionMode) {
        final byte[] tiffData = buildTiffData(corruptionMode);
        final ByteArrayOutputStream exif = new ByteArrayOutputStream();
        exif.write("Exif\u0000\u0000".getBytes(StandardCharsets.US_ASCII), 0, 6);
        exif.write(tiffData, 0, tiffData.length);
        return exif.toByteArray();
    }

    private static byte[] buildSof0Segment() {
        final ByteArrayOutputStream sof0 = new ByteArrayOutputStream();
        sof0.write(0x08);
        writeUnsignedShort(sof0, 0x0001);
        writeUnsignedShort(sof0, 0x0001);
        sof0.write(0x01);
        sof0.write(0x01);
        sof0.write(0x11);
        sof0.write(0x00);
        return sof0.toByteArray();
    }

    private static byte[] buildSosSegment() {
        return new byte[] { 0x01, 0x01, 0x00, 0x00, 0x3f, 0x00 };
    }

    private static byte[] buildTiffData(final ThumbnailCorruptionMode corruptionMode) {
        final ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        tiff.write('I');
        tiff.write('I');
        writeUnsignedShortLe(tiff, 42);
        writeUnsignedIntLe(tiff, 8);

        writeUnsignedShortLe(tiff, 0);
        writeUnsignedIntLe(tiff, THUMBNAIL_IFD_OFFSET);

        writeUnsignedShortLe(tiff, 2);
        writeIfdLongField(tiff, JPEG_INTERCHANGE_FORMAT_TAG, thumbnailOffset(corruptionMode));
        writeIfdLongField(tiff, JPEG_INTERCHANGE_FORMAT_LENGTH_TAG, thumbnailLength(corruptionMode));
        writeUnsignedIntLe(tiff, nextIfdOffset(corruptionMode));
        tiff.write(EMBEDDED_THUMBNAIL, 0, EMBEDDED_THUMBNAIL.length);
        return tiff.toByteArray();
    }

    private static int nextIfdOffset(final ThumbnailCorruptionMode corruptionMode) {
        if (ThumbnailCorruptionMode.INVALID_INTERNAL_TIFF == corruptionMode) {
            return 0xffff_ffff;
        }
        return 0;
    }

    private static int thumbnailLength(final ThumbnailCorruptionMode corruptionMode) {
        if (ThumbnailCorruptionMode.LENGTH_EXCEEDS_REMAINING_SIZE == corruptionMode) {
            return EMBEDDED_THUMBNAIL.length + 8;
        }
        return EMBEDDED_THUMBNAIL.length;
    }

    private static int thumbnailOffset(final ThumbnailCorruptionMode corruptionMode) {
        if (ThumbnailCorruptionMode.OFFSET_OUTSIDE_FILE == corruptionMode) {
            return 0x0001_0000;
        }
        return THUMBNAIL_DATA_OFFSET;
    }

    private static void writeIfdLongField(final ByteArrayOutputStream output, final int tag, final int value) {
        writeUnsignedShortLe(output, tag);
        writeUnsignedShortLe(output, TIFF_TYPE_LONG);
        writeUnsignedIntLe(output, 1);
        writeUnsignedIntLe(output, value);
    }

    private static void writeMarker(final ByteArrayOutputStream output, final int marker) {
        output.write(marker >> 8 & 0xff);
        output.write(marker & 0xff);
    }

    private static void writeSegment(final ByteArrayOutputStream output, final int marker, final byte[] data) {
        writeMarker(output, marker);
        writeUnsignedShort(output, data.length + 2);
        output.write(data, 0, data.length);
    }

    private static void writeUnsignedIntLe(final ByteArrayOutputStream output, final int value) {
        output.write(value & 0xff);
        output.write(value >> 8 & 0xff);
        output.write(value >> 16 & 0xff);
        output.write(value >> 24 & 0xff);
    }

    private static void writeUnsignedShort(final ByteArrayOutputStream output, final int value) {
        output.write(value >> 8 & 0xff);
        output.write(value & 0xff);
    }

    private static void writeUnsignedShortLe(final ByteArrayOutputStream output, final int value) {
        output.write(value & 0xff);
        output.write(value >> 8 & 0xff);
    }
}
