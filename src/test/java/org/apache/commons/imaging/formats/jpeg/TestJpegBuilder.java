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
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * In-memory JPEG builder for testing corrupted EXIF thumbnail scenarios.
 * Constructs a complete JPEG byte array with SOI, APP0, APP1(EXIF), DQT, SOF0, DHT, SOS, and EOI markers.
 * The APP1 segment contains a valid TIFF structure with intentionally corrupted thumbnail data.
 */
public final class TestJpegBuilder {

    private static final int IFD_ENTRY_SIZE = 12;

    // offsets within TIFF data
    private static final int OFFSET_IFD0 = 8;
    private static final int OFFSET_NEXT_IFD = OFFSET_IFD0 + 2 + IFD_ENTRY_SIZE * 7;
    private static final int OFFSET_BPS_DATA = OFFSET_NEXT_IFD + 4;
    private static final int OFFSET_THUMBNAIL = OFFSET_BPS_DATA + 6;

    // JPEG marker constants
    private static final int SOI = 0xFFD8;
    private static final int EOI = 0xFFD9;
    private static final int APP0_MARKER = 0xFFE0;
    private static final int APP1_MARKER = 0xFFE1;
    private static final int DQT_MARKER = 0xFFDB;
    private static final int SOF0_MARKER = 0xFFC0;
    private static final int DHT_MARKER = 0xFFC4;
    private static final int SOS_MARKER = 0xFFDA;

    // TIFF type codes
    private static final int TYPE_SHORT = 3;
    private static final int TYPE_LONG = 4;

    // TIFF tag codes
    private static final int TAG_IMAGE_WIDTH = 0x0100;
    private static final int TAG_IMAGE_LENGTH = 0x0101;
    private static final int TAG_BITS_PER_SAMPLE = 0x0102;
    private static final int TAG_COMPRESSION = 0x0103;
    private static final int TAG_PHOTOMETRIC_INTERPRETATION = 0x0106;
    private static final int TAG_JPEG_INTERCHANGE_FORMAT = 0x0201;
    private static final int TAG_JPEG_INTERCHANGE_FORMAT_LENGTH = 0x0202;

    private TestJpegBuilder() {
    }

    /**
     * Builds a JPEG where the thumbnail offset points beyond the end of the byte array.
     */
    public static byte[] buildWithThumbnailOffsetBeyondEnd() {
        final int jpegInterchangeFormat = Integer.MAX_VALUE;
        final int jpegInterchangeFormatLength = 100;
        final long nextIfdOffset = 0L;
        return buildJpeg(jpegInterchangeFormat, jpegInterchangeFormatLength, nextIfdOffset);
    }

    /**
     * Builds a JPEG where the thumbnail offset is valid but offset + length exceeds the file size.
     */
    public static byte[] buildWithThumbnailLengthExceeds() {
        final int jpegInterchangeFormat = 50;
        final int jpegInterchangeFormatLength = 1000;
        final long nextIfdOffset = 0L;
        return buildJpeg(jpegInterchangeFormat, jpegInterchangeFormatLength, nextIfdOffset);
    }

    /**
     * Builds a JPEG where the TIFF Next IFD offset is 0xFFFFFFFF, causing internal parsing to fail.
     */
    public static byte[] buildWithCorruptedNextIfd() {
        final int jpegInterchangeFormat = OFFSET_THUMBNAIL;
        final int jpegInterchangeFormatLength = 22;
        final long nextIfdOffset = 0xFFFFFFFFL;
        return buildJpeg(jpegInterchangeFormat, jpegInterchangeFormatLength, nextIfdOffset);
    }

    private static byte[] buildJpeg(final int jpegInterchangeFormat, final int jpegInterchangeFormatLength, final long nextIfdOffset) {
        final byte[] tiffData = buildTiff(jpegInterchangeFormat, jpegInterchangeFormatLength, nextIfdOffset);
        final byte[] exifPrefix = "Exif\0\0".getBytes(StandardCharsets.US_ASCII);
        final byte[] app1Data = new byte[exifPrefix.length + tiffData.length];
        System.arraycopy(exifPrefix, 0, app1Data, 0, exifPrefix.length);
        System.arraycopy(tiffData, 0, app1Data, exifPrefix.length, tiffData.length);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            writeMarker(baos, SOI);
            writeSegment(baos, APP0_MARKER, buildApp0Data());
            writeSegment(baos, APP1_MARKER, app1Data);
            writeSegment(baos, DQT_MARKER, buildDqtData());
            writeSegment(baos, SOF0_MARKER, buildSof0Data());
            writeSegment(baos, DHT_MARKER, buildDhtData());
            writeSegment(baos, SOS_MARKER, buildSosData());
            writeMarker(baos, EOI);
        } catch (final IOException e) {
            throw new RuntimeException("Failed to build JPEG", e);
        }
        return baos.toByteArray();
    }

    private static byte[] buildTiff(final int jpegInterchangeFormat, final int jpegInterchangeFormatLength, final long nextIfdOffset) {
        final int entryCount = 7;
        final int totalSize = OFFSET_THUMBNAIL + 22;

        final byte[] data = new byte[totalSize];

        // TIFF header: "II" + magic 42 + IFD0 offset
        data[0] = 'I';
        data[1] = 'I';
        writeShortLE(data, 2, 42);
        writeIntLE(data, 4, OFFSET_IFD0);

        // IFD0 entry count
        writeShortLE(data, OFFSET_IFD0, entryCount);

        // Entry 1: ImageWidth (LONG, count=1, value=10)
        writeIfdEntry(data, OFFSET_IFD0 + 2, TAG_IMAGE_WIDTH, TYPE_LONG, 1, 10);

        // Entry 2: ImageLength (LONG, count=1, value=10)
        writeIfdEntry(data, OFFSET_IFD0 + 2 + IFD_ENTRY_SIZE, TAG_IMAGE_LENGTH, TYPE_LONG, 1, 10);

        // Entry 3: BitsPerSample (SHORT, count=3, offset to data after IFD)
        writeIfdEntry(data, OFFSET_IFD0 + 2 + IFD_ENTRY_SIZE * 2, TAG_BITS_PER_SAMPLE, TYPE_SHORT, 3, OFFSET_BPS_DATA);

        // Entry 4: Compression (SHORT, count=1, value=6 = JPEG)
        writeIfdEntry(data, OFFSET_IFD0 + 2 + IFD_ENTRY_SIZE * 3, TAG_COMPRESSION, TYPE_SHORT, 1, 6);

        // Entry 5: PhotometricInterpretation (SHORT, count=1, value=2 = RGB)
        writeIfdEntry(data, OFFSET_IFD0 + 2 + IFD_ENTRY_SIZE * 4, TAG_PHOTOMETRIC_INTERPRETATION, TYPE_SHORT, 1, 2);

        // Entry 6: JPEGInterchangeFormat (LONG, count=1) - CORRUPTED
        writeIfdEntry(data, OFFSET_IFD0 + 2 + IFD_ENTRY_SIZE * 5, TAG_JPEG_INTERCHANGE_FORMAT, TYPE_LONG, 1, jpegInterchangeFormat);

        // Entry 7: JPEGInterchangeFormatLength (LONG, count=1) - CORRUPTED
        writeIfdEntry(data, OFFSET_IFD0 + 2 + IFD_ENTRY_SIZE * 6, TAG_JPEG_INTERCHANGE_FORMAT_LENGTH, TYPE_LONG, 1, jpegInterchangeFormatLength);

        // Next IFD offset
        writeIntLE(data, OFFSET_NEXT_IFD, nextIfdOffset);

        // BitsPerSample data: 8, 8, 8 (3 shorts)
        writeShortLE(data, OFFSET_BPS_DATA, 8);
        writeShortLE(data, OFFSET_BPS_DATA + 2, 8);
        writeShortLE(data, OFFSET_BPS_DATA + 4, 8);

        // Thumbnail JPEG: SOI + padding + EOI
        data[OFFSET_THUMBNAIL] = (byte) 0xFF;
        data[OFFSET_THUMBNAIL + 1] = (byte) 0xD8;
        for (int i = 2; i < 20; i++) {
            data[OFFSET_THUMBNAIL + i] = (byte) 0x00;
        }
        data[OFFSET_THUMBNAIL + 20] = (byte) 0xFF;
        data[OFFSET_THUMBNAIL + 21] = (byte) 0xD9;

        return data;
    }

    private static void writeIfdEntry(final byte[] data, final int offset, final int tag, final int type, final int count, final long value) {
        writeShortLE(data, offset, tag);
        writeShortLE(data, offset + 2, type);
        writeIntLE(data, offset + 4, count);
        writeIntLE(data, offset + 8, value);
    }

    private static void writeShortLE(final byte[] data, final int offset, final int value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
    }

    private static void writeIntLE(final byte[] data, final int offset, final long value) {
        data[offset] = (byte) (value & 0xFF);
        data[offset + 1] = (byte) ((value >> 8) & 0xFF);
        data[offset + 2] = (byte) ((value >> 16) & 0xFF);
        data[offset + 3] = (byte) ((value >> 24) & 0xFF);
    }

    private static void writeMarker(final ByteArrayOutputStream baos, final int marker) throws IOException {
        baos.write((marker >> 8) & 0xFF);
        baos.write(marker & 0xFF);
    }

    private static void writeSegment(final ByteArrayOutputStream baos, final int marker, final byte[] data) throws IOException {
        writeMarker(baos, marker);
        final int length = data.length + 2;
        baos.write((length >> 8) & 0xFF);
        baos.write(length & 0xFF);
        baos.write(data);
    }

    private static byte[] buildApp0Data() {
        // JFIF: "JFIF\0" + version 1.01 + units + density + thumbnail 0x00
        final byte[] data = new byte[14];
        final byte[] jfif = "JFIF\0".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(jfif, 0, data, 0, jfif.length);
        data[5] = 1;
        data[6] = 1;
        data[7] = 0;
        data[8] = 0;
        data[9] = 0;
        data[10] = 1;
        data[11] = 0;
        data[12] = 1;
        data[13] = 0;
        return data;
    }

    private static byte[] buildDqtData() {
        // Quantization table: table 0, 64 bytes of 1s
        final byte[] data = new byte[65];
        data[0] = 0;
        for (int i = 1; i < 65; i++) {
            data[i] = 1;
        }
        return data;
    }

    private static byte[] buildSof0Data() {
        // SOF0: precision=8, height=10, width=10, 3 components (Y=1, Cb=2, Cr=3)
        final byte[] data = new byte[17];
        data[0] = 8;
        data[1] = 0;
        data[2] = 10;
        data[3] = 0;
        data[4] = 10;
        data[5] = 3;
        data[6] = 1;
        data[7] = 0x11;
        data[8] = 0;
        data[9] = 2;
        data[10] = 0x11;
        data[11] = 1;
        data[12] = 3;
        data[13] = 0x11;
        data[14] = 1;
        data[15] = 0;
        data[16] = 0;
        return data;
    }

    private static byte[] buildDhtData() {
        // Huffman table: DC table 0, 2 AC tables
        final byte[] dcTable0 = buildMinimalHuffmanTable(0x00);
        final byte[] acTable0 = buildMinimalHuffmanTable(0x10);
        final byte[] acTable1 = buildMinimalHuffmanTable(0x11);
        final byte[] data = new byte[dcTable0.length + acTable0.length + acTable1.length];
        int pos = 0;
        System.arraycopy(dcTable0, 0, data, pos, dcTable0.length);
        pos += dcTable0.length;
        System.arraycopy(acTable0, 0, data, pos, acTable0.length);
        pos += acTable0.length;
        System.arraycopy(acTable1, 0, data, pos, acTable1.length);
        return data;
    }

    private static byte[] buildMinimalHuffmanTable(final int tableClass) {
        final byte[] data = new byte[18];
        data[0] = (byte) tableClass;
        // 16 bytes of counts: all 0
        // 1 byte of value: 0
        data[17] = 0;
        return data;
    }

    private static byte[] buildSosData() {
        // SOS: 3 components (Y=1, Cb=2, Cr=3), table selectors, spectral selection
        final byte[] data = new byte[8];
        data[0] = 3;
        data[1] = 1;
        data[2] = 0;
        data[3] = 2;
        data[4] = 0x11;
        data[5] = 3;
        data[6] = 0x11;
        data[7] = 0;
        return data;
    }
}