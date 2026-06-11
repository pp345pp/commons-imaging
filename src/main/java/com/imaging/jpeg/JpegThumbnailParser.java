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

import java.util.Arrays;

import org.apache.commons.imaging.ImagingFormatException;

/**
 * Minimal parser that extracts the JPEG thumbnail stored inside the EXIF
 * (APP1) segment of a JPEG file. The parser only understands the structural
 * information needed to walk the SOI/APP1/TIFF headers and retrieve the
 * thumbnail bytes referenced by the {@code JPEGInterchangeFormat} and
 * {@code JPEGInterchangeFormatLength} TIFF tags.
 */
public final class JpegThumbnailParser {

    /** TIFF tag id for the offset (from the TIFF header) of the thumbnail JPEG. */
    public static final int TAG_JPEG_INTERCHANGE_FORMAT = 0x0201;

    /** TIFF tag id for the length of the thumbnail JPEG bytes. */
    public static final int TAG_JPEG_INTERCHANGE_FORMAT_LENGTH = 0x0202;

    /** {@code Exif\0\0} identifier that precedes the TIFF header inside an APP1. */
    private static final byte[] EXIF_IDENTIFIER = { 'E', 'x', 'i', 'f', 0, 0 };

    private static final short SOI = (short) 0xFFD8;
    private static final short APP1 = (short) 0xFFE1;
    private static final int TIFF_HEADER_SIZE = 8;

    private JpegThumbnailParser() {
    }

    /**
     * Extracts the EXIF-embedded thumbnail from the given JPEG byte array.
     *
     * @param jpegBytes JPEG file bytes (starting with SOI).
     * @return the raw thumbnail JPEG bytes.
     * @throws ImagingFormatException if the JPEG is malformed, has no EXIF/APP1,
     *                                has no thumbnail, or the thumbnail is
     *                                corrupted (offset/length past end of file or
     *                                invalid internal TIFF structure).
     */
    public static byte[] extractThumbnail(final byte[] jpegBytes) {
        if (jpegBytes == null || jpegBytes.length < 4) {
            throw new ImagingFormatException("Thumbnail data missing or too short");
        }
        if (readU16BE(jpegBytes, 0) != (SOI & 0xFFFF)) {
            throw new ImagingFormatException("Thumbnail: not a valid JPEG (missing SOI)");
        }

        final ExifSegment exif = findExifApp1(jpegBytes);
        if (exif == null) {
            throw new ImagingFormatException("Thumbnail EXIF metadata not present");
        }

        final int tiffStart = exif.payloadOffset + EXIF_IDENTIFIER.length;
        if (tiffStart + TIFF_HEADER_SIZE > jpegBytes.length) {
            throw new ImagingFormatException("Thumbnail TIFF header truncated");
        }

        final ByteOrder byteOrder = readTiffByteOrder(jpegBytes, tiffStart);
        final int magic = byteOrder.readU16(jpegBytes, tiffStart + 2);
        if (magic != 42) {
            throw new ImagingFormatException("Thumbnail TIFF magic number invalid");
        }

        final int firstIfdOffset = byteOrder.readU32(jpegBytes, tiffStart + 4);
        if (firstIfdOffset < TIFF_HEADER_SIZE || tiffStart + firstIfdOffset > jpegBytes.length) {
            throw new ImagingFormatException("Thumbnail TIFF IFD0 offset invalid: %d", firstIfdOffset);
        }

        int ifdOffset = firstIfdOffset;
        long thumbnailOffset = -1L;
        long thumbnailLength = -1L;
        int ifdCount = 0;
        while (ifdOffset != 0) {
            ifdCount++;
            if (ifdCount > 16) {
                throw new ImagingFormatException("Thumbnail TIFF has too many IFDs, possible corruption");
            }
            final int abs = tiffStart + ifdOffset;
            if (abs + 2 > jpegBytes.length) {
                throw new ImagingFormatException("Thumbnail TIFF IFD header truncated");
            }
            final int entries = byteOrder.readU16(jpegBytes, abs);
            final int ifdEnd = abs + 2 + entries * 12 + 4;
            if (ifdEnd > jpegBytes.length) {
                throw new ImagingFormatException("Thumbnail TIFF IFD truncated: %d entries at offset %d", entries, ifdOffset);
            }
            for (int i = 0; i < entries; i++) {
                final int entry = abs + 2 + i * 12;
                final int tag = byteOrder.readU16(jpegBytes, entry);
                final int type = byteOrder.readU16(jpegBytes, entry + 2);
                final long count = byteOrder.readU32(jpegBytes, entry + 4);
                if (tag == TAG_JPEG_INTERCHANGE_FORMAT && type == 4 /* LONG */ && count == 1) {
                    thumbnailOffset = byteOrder.readU32(jpegBytes, entry + 8);
                } else if (tag == TAG_JPEG_INTERCHANGE_FORMAT_LENGTH && type == 4 /* LONG */ && count == 1) {
                    thumbnailLength = byteOrder.readU32(jpegBytes, entry + 8);
                }
            }
            final int next = byteOrder.readU32(jpegBytes, abs + 2 + entries * 12);
            if (next == 0xFFFFFFFF) {
                throw new ImagingFormatException("Thumbnail TIFF Next IFD offset corrupted (0xFFFFFFFF)");
            }
            if (next != 0 && (next < TIFF_HEADER_SIZE || tiffStart + next > jpegBytes.length)) {
                throw new ImagingFormatException("Thumbnail TIFF Next IFD offset invalid: %d", next);
            }
            ifdOffset = next;
        }

        if (thumbnailOffset < 0 || thumbnailLength < 0) {
            throw new ImagingFormatException("Thumbnail not present in EXIF metadata");
        }

        final long absOffset = tiffStart + thumbnailOffset;
        if (absOffset >= jpegBytes.length || absOffset < 0) {
            throw new ImagingFormatException("Thumbnail offset (%d) points past end of file (%d)", absOffset, jpegBytes.length);
        }
        if (absOffset + thumbnailLength > jpegBytes.length) {
            throw new ImagingFormatException("Thumbnail length (%d) exceeds remaining bytes at offset %d (file size %d)", thumbnailLength, absOffset, jpegBytes.length);
        }

        final int start = (int) absOffset;
        final int length = (int) thumbnailLength;
        return Arrays.copyOfRange(jpegBytes, start, start + length);
    }

    private static ExifSegment findExifApp1(final byte[] jpegBytes) {
        int pos = 2; // skip SOI
        while (pos + 4 <= jpegBytes.length) {
            final int marker = readU16BE(jpegBytes, pos);
            if ((marker & 0xFF00) != 0xFF00) {
                throw new ImagingFormatException("Thumbnail: invalid JPEG marker at offset %d", pos);
            }
            final int length = readU16BE(jpegBytes, pos + 2);
            if (length < 2) {
                throw new ImagingFormatException("Thumbnail: JPEG segment length invalid");
            }
            final int payload = pos + 4;
            final int segmentEnd = pos + 2 + length;
            if (segmentEnd > jpegBytes.length) {
                throw new ImagingFormatException("Thumbnail: JPEG segment past end of file");
            }
            if (marker == (APP1 & 0xFFFF) && length >= EXIF_IDENTIFIER.length + 2
                    && startsWith(jpegBytes, payload, EXIF_IDENTIFIER)) {
                return new ExifSegment(payload, segmentEnd);
            }
            pos = segmentEnd;
        }
        return null;
    }

    private static ByteOrder readTiffByteOrder(final byte[] bytes, final int offset) {
        if (bytes[offset] == 'I' && bytes[offset + 1] == 'I') {
            return ByteOrder.LITTLE_ENDIAN;
        }
        if (bytes[offset] == 'M' && bytes[offset + 1] == 'M') {
            return ByteOrder.BIG_ENDIAN;
        }
        throw new ImagingFormatException("Thumbnail TIFF byte-order marker invalid");
    }

    private static boolean startsWith(final byte[] source, final int offset, final byte[] prefix) {
        if (offset + prefix.length > source.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (source[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static int readU16BE(final byte[] bytes, final int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private enum ByteOrder {
        LITTLE_ENDIAN {
            @Override
            int readU16(final byte[] bytes, final int offset) {
                return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
            }

            @Override
            int readU32(final byte[] bytes, final int offset) {
                return (bytes[offset] & 0xFF)
                        | ((bytes[offset + 1] & 0xFF) << 8)
                        | ((bytes[offset + 2] & 0xFF) << 16)
                        | ((bytes[offset + 3] & 0xFF) << 24);
            }
        },
        BIG_ENDIAN {
            @Override
            int readU16(final byte[] bytes, final int offset) {
                return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
            }

            @Override
            int readU32(final byte[] bytes, final int offset) {
                return ((bytes[offset] & 0xFF) << 24)
                        | ((bytes[offset + 1] & 0xFF) << 16)
                        | ((bytes[offset + 2] & 0xFF) << 8)
                        | (bytes[offset + 3] & 0xFF);
            }
        };

        abstract int readU16(byte[] bytes, int offset);

        abstract int readU32(byte[] bytes, int offset);
    }

    private static final class ExifSegment {
        final int payloadOffset;
        final int endOffset;

        ExifSegment(final int payloadOffset, final int endOffset) {
            this.payloadOffset = payloadOffset;
            this.endOffset = endOffset;
        }
    }
}
