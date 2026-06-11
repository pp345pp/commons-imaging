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

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.ImagingFormatException;
import org.apache.commons.imaging.bytesource.ByteSource;

public final class JpegThumbnailParser {

    private static final int TIFF_DIRECTORY_ENTRY_SIZE = 12;
    private static final int TIFF_TYPE_LONG = 4;
    private static final int TIFF_VERSION = 42;
    private static final int TAG_JPEG_INTERCHANGE_FORMAT = 0x0201;
    private static final int TAG_JPEG_INTERCHANGE_FORMAT_LENGTH = 0x0202;

    private static final class Directory {
        private final Map<Integer, Long> fields;
        private final long nextOffset;

        private Directory(final Map<Integer, Long> fields, final long nextOffset) {
            this.fields = fields;
            this.nextOffset = nextOffset;
        }
    }

    private JpegThumbnailParser() {
    }

    public static byte[] extractThumbnail(final byte[] jpegBytes) {
        final byte[] exifBytes = extractExifBytes(jpegBytes);
        final ByteOrder byteOrder = readByteOrder(exifBytes);
        final int firstIfdOffset = readUnsignedInt(exifBytes, 4, byteOrder, "Thumbnail first IFD offset");
        final Directory rootDirectory = readDirectory(exifBytes, firstIfdOffset, byteOrder, "Thumbnail root IFD");
        if (rootDirectory.nextOffset <= 0) {
            throw new ImagingFormatException("Thumbnail IFD is missing");
        }

        final Directory thumbnailDirectory = readDirectory(exifBytes, toInt(rootDirectory.nextOffset, "Thumbnail IFD offset"), byteOrder, "Thumbnail IFD");
        if (thumbnailDirectory.nextOffset != 0 && thumbnailDirectory.nextOffset >= exifBytes.length) {
            throw new ImagingFormatException("Thumbnail next IFD offset is invalid: %d", thumbnailDirectory.nextOffset);
        }

        final Long thumbnailOffset = thumbnailDirectory.fields.get(TAG_JPEG_INTERCHANGE_FORMAT);
        final Long thumbnailLength = thumbnailDirectory.fields.get(TAG_JPEG_INTERCHANGE_FORMAT_LENGTH);
        if (thumbnailOffset == null || thumbnailLength == null) {
            throw new ImagingFormatException("Thumbnail JPEG fields are missing");
        }

        if (thumbnailOffset < 0 || thumbnailOffset >= exifBytes.length) {
            throw new ImagingFormatException("Thumbnail offset is outside TIFF data: %d", thumbnailOffset);
        }
        if (thumbnailLength < 0) {
            throw new ImagingFormatException("Thumbnail length is invalid: %d", thumbnailLength);
        }

        final long thumbnailEnd = thumbnailOffset + thumbnailLength;
        if (thumbnailEnd > exifBytes.length) {
            throw new ImagingFormatException("Thumbnail length extends beyond TIFF data: offset=%d, length=%d", thumbnailOffset, thumbnailLength);
        }

        final int start = toInt(thumbnailOffset, "Thumbnail offset");
        final int end = toInt(thumbnailEnd, "Thumbnail end");
        return Arrays.copyOfRange(exifBytes, start, end);
    }

    private static Directory readDirectory(final byte[] tiffBytes, final int offset, final ByteOrder byteOrder, final String name) {
        ensureRange(tiffBytes, offset, 2, name + " entry count");
        final int entryCount = readUnsignedShort(tiffBytes, offset, byteOrder, name + " entry count");
        final long directoryLength = 2L + entryCount * (long) TIFF_DIRECTORY_ENTRY_SIZE + 4L;
        ensureRange(tiffBytes, offset, directoryLength, name);

        final Map<Integer, Long> fields = new HashMap<>();
        int entryOffset = offset + 2;
        for (int i = 0; i < entryCount; i++) {
            final int tag = readUnsignedShort(tiffBytes, entryOffset, byteOrder, name + " tag");
            final int type = readUnsignedShort(tiffBytes, entryOffset + 2, byteOrder, name + " type");
            final int count = readUnsignedInt(tiffBytes, entryOffset + 4, byteOrder, name + " count");
            final int value = readUnsignedInt(tiffBytes, entryOffset + 8, byteOrder, name + " value");
            if (type == TIFF_TYPE_LONG && count == 1) {
                fields.put(tag, (long) value);
            }
            entryOffset += TIFF_DIRECTORY_ENTRY_SIZE;
        }

        final long nextOffset = readUnsignedInt(tiffBytes, entryOffset, byteOrder, name + " next IFD offset");
        return new Directory(fields, nextOffset);
    }

    private static byte[] extractExifBytes(final byte[] jpegBytes) {
        try {
            final byte[] exifBytes = new JpegImageParser().getExifRawData(ByteSource.array(jpegBytes, "thumbnail-jpeg"));
            if (exifBytes == null) {
                throw new ImagingFormatException("Thumbnail EXIF data is missing");
            }
            return exifBytes;
        } catch (final ImagingFormatException e) {
            throw e;
        } catch (final ImagingException | IOException e) {
            throw new ImagingFormatException("Thumbnail EXIF data is invalid", e);
        }
    }

    private static void ensureRange(final byte[] data, final long offset, final long length, final String description) {
        if (offset < 0 || length < 0 || offset > data.length || offset + length > data.length) {
            throw new ImagingFormatException("%s is outside TIFF data", description);
        }
    }

    private static ByteOrder readByteOrder(final byte[] tiffBytes) {
        ensureRange(tiffBytes, 0, 8, "Thumbnail TIFF header");
        final int first = tiffBytes[0] & 0xff;
        final int second = tiffBytes[1] & 0xff;
        final ByteOrder byteOrder;
        if (first == 'I' && second == 'I') {
            byteOrder = ByteOrder.LITTLE_ENDIAN;
        } else if (first == 'M' && second == 'M') {
            byteOrder = ByteOrder.BIG_ENDIAN;
        } else {
            throw new ImagingFormatException("Thumbnail TIFF byte order is invalid");
        }

        final int version = readUnsignedShort(tiffBytes, 2, byteOrder, "Thumbnail TIFF version");
        if (version != TIFF_VERSION) {
            throw new ImagingFormatException("Thumbnail TIFF version is invalid: %d", version);
        }
        return byteOrder;
    }

    private static int readUnsignedShort(final byte[] data, final int offset, final ByteOrder byteOrder, final String description) {
        ensureRange(data, offset, 2, description);
        if (ByteOrder.LITTLE_ENDIAN.equals(byteOrder)) {
            return (data[offset] & 0xff) | (data[offset + 1] & 0xff) << 8;
        }
        return (data[offset] & 0xff) << 8 | data[offset + 1] & 0xff;
    }

    private static int readUnsignedInt(final byte[] data, final int offset, final ByteOrder byteOrder, final String description) {
        ensureRange(data, offset, 4, description);
        final long value;
        if (ByteOrder.LITTLE_ENDIAN.equals(byteOrder)) {
            value = (long) (data[offset] & 0xff) | (long) (data[offset + 1] & 0xff) << 8 | (long) (data[offset + 2] & 0xff) << 16
                    | (long) (data[offset + 3] & 0xff) << 24;
        } else {
            value = (long) (data[offset] & 0xff) << 24 | (long) (data[offset + 1] & 0xff) << 16 | (long) (data[offset + 2] & 0xff) << 8
                    | (long) (data[offset + 3] & 0xff);
        }
        return toInt(value, description);
    }

    private static int toInt(final long value, final String description) {
        if (value < 0 || value > Integer.MAX_VALUE) {
            throw new ImagingFormatException("%s is invalid: %d", description, value);
        }
        return (int) value;
    }
}
