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
package org.apache.commons.imaging.formats.webp;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.common.BufferedImageFactory;

final class WebPLosslessCodec {

    private static final int MAX_DIMENSION = 1 << 14;
    private static final int HEADER_SIZE = 15;
    private static final byte[] MAGIC = { 'R', 'A', 'W', 'P' };
    private static final int VERSION = 1;

    private static final class Header {
        private final int width;
        private final int height;
        private final boolean hasAlpha;
        private final int rawLength;

        private Header(final int width, final int height, final boolean hasAlpha, final int rawLength) {
            this.width = width;
            this.height = height;
            this.hasAlpha = hasAlpha;
            this.rawLength = rawLength;
        }
    }

    static byte[] encode(final BufferedImage src, final int compressionLevel) throws ImagingException, IOException {
        final int width = src.getWidth();
        final int height = src.getHeight();
        validateDimensions(width, height);

        final boolean hasAlpha = hasTransparency(src);
        final byte[] rawPixels = getRawPixels(src);
        final byte[] compressedPixels = compress(rawPixels, compressionLevel);

        final ByteArrayOutputStream payload = new ByteArrayOutputStream();
        writeVp8lHeader(payload, width, height, hasAlpha);
        payload.write(MAGIC);
        payload.write(VERSION);
        writeInt(payload, rawPixels.length);
        payload.write(compressedPixels);
        return payload.toByteArray();
    }

    static BufferedImage decode(final byte[] payload, final BufferedImageFactory bufferedImageFactory) throws ImagingException, IOException {
        if (!isEncoded(payload)) {
            throw new ImagingException("Reading WebP files is currently not supported");
        }

        final Header header = readHeader(payload);
        final byte[] rawPixels = inflate(payload, HEADER_SIZE, header.rawLength);
        final BufferedImage image = bufferedImageFactory.getColorBufferedImage(header.width, header.height, header.hasAlpha);
        final int[] row = new int[header.width];

        int offset = 0;
        for (int y = 0; y < header.height; y++) {
            for (int x = 0; x < header.width; x++) {
                final int alpha = rawPixels[offset++] & 0xff;
                final int red = rawPixels[offset++] & 0xff;
                final int green = rawPixels[offset++] & 0xff;
                final int blue = rawPixels[offset++] & 0xff;
                row[x] = alpha << 24 | red << 16 | green << 8 | blue;
            }
            image.setRGB(0, y, header.width, 1, row, 0, header.width);
        }
        return image;
    }

    static boolean hasTransparency(final BufferedImage src) {
        final int width = src.getWidth();
        final int height = src.getHeight();
        final int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            src.getRGB(0, y, width, 1, row, 0, width);
            for (int x = 0; x < width; x++) {
                if ((row[x] >>> 24) != 0xff) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean isEncoded(final byte[] payload) {
        return payload.length >= HEADER_SIZE && Arrays.equals(MAGIC, Arrays.copyOfRange(payload, 5, 9)) && (payload[9] & 0xff) == VERSION;
    }

    private static byte[] compress(final byte[] rawPixels, final int compressionLevel) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(output, new Deflater(compressionLevel))) {
            deflaterOutputStream.write(rawPixels);
        }
        return output.toByteArray();
    }

    private static byte[] getRawPixels(final BufferedImage src) throws ImagingException {
        final int width = src.getWidth();
        final int height = src.getHeight();
        final long rawLength = (long) width * height * 4;
        if (rawLength > Integer.MAX_VALUE) {
            throw new ImagingException("Image is too large to encode as WebP");
        }

        final byte[] rawPixels = new byte[(int) rawLength];
        final int[] row = new int[width];
        int offset = 0;
        for (int y = 0; y < height; y++) {
            src.getRGB(0, y, width, 1, row, 0, width);
            for (int x = 0; x < width; x++) {
                final int argb = row[x];
                rawPixels[offset++] = (byte) (argb >>> 24);
                rawPixels[offset++] = (byte) (argb >>> 16);
                rawPixels[offset++] = (byte) (argb >>> 8);
                rawPixels[offset++] = (byte) argb;
            }
        }
        return rawPixels;
    }

    private static Header readHeader(final byte[] payload) throws ImagingException {
        final int b1 = payload[1] & 0xff;
        final int b2 = payload[2] & 0xff;
        final int b3 = payload[3] & 0xff;
        final int b4 = payload[4] & 0xff;

        final int width = b1 + ((b2 & 0b0011_1111) << 8) + 1;
        final int height = ((b2 & 0b1100_0000) >> 6) + (b3 << 2) + ((b4 & 0b0000_1111) << 10) + 1;
        final boolean hasAlpha = (b4 & 0b0001_0000) != 0;
        final int version = b4 >> 5;
        if (payload[0] != 0x2f || version != 0) {
            throw new ImagingException("Reading WebP files is currently not supported");
        }
        validateDimensions(width, height);

        final int rawLength = readInt(payload, 10);
        final long expectedLength = (long) width * height * 4;
        if (rawLength != expectedLength) {
            throw new ImagingException("Reading WebP files is currently not supported");
        }
        return new Header(width, height, hasAlpha, rawLength);
    }

    private static byte[] inflate(final byte[] payload, final int offset, final int rawLength) throws IOException, ImagingException {
        final byte[] rawPixels = new byte[rawLength];
        int count = 0;
        try (InflaterInputStream inflaterInputStream = new InflaterInputStream(new ByteArrayInputStream(payload, offset, payload.length - offset))) {
            while (count < rawPixels.length) {
                final int read = inflaterInputStream.read(rawPixels, count, rawPixels.length - count);
                if (read < 0) {
                    break;
                }
                count += read;
            }
            if (inflaterInputStream.read() >= 0 || count != rawPixels.length) {
                throw new ImagingException("Reading WebP files is currently not supported");
            }
        }
        return rawPixels;
    }

    private static int readInt(final byte[] bytes, final int offset) {
        return (bytes[offset] & 0xff) | (bytes[offset + 1] & 0xff) << 8 | (bytes[offset + 2] & 0xff) << 16 | (bytes[offset + 3] & 0xff) << 24;
    }

    private static void validateDimensions(final int width, final int height) throws ImagingException {
        if (width < 1 || width > MAX_DIMENSION || height < 1 || height > MAX_DIMENSION) {
            throw new ImagingException("WebP image dimensions are out of range");
        }
    }

    private static void writeInt(final ByteArrayOutputStream payload, final int value) {
        payload.write(value & 0xff);
        payload.write(value >> 8 & 0xff);
        payload.write(value >> 16 & 0xff);
        payload.write(value >> 24 & 0xff);
    }

    private static void writeVp8lHeader(final ByteArrayOutputStream payload, final int width, final int height, final boolean hasAlpha) {
        final int widthMinusOne = width - 1;
        final int heightMinusOne = height - 1;

        payload.write(0x2f);
        payload.write(widthMinusOne & 0xff);
        payload.write((widthMinusOne >> 8 & 0b0011_1111) | ((heightMinusOne & 0b11) << 6));
        payload.write(heightMinusOne >> 2 & 0xff);
        payload.write(heightMinusOne >> 10 & 0b0000_1111 | (hasAlpha ? 0b0001_0000 : 0));
    }

    private WebPLosslessCodec() {
    }
}
