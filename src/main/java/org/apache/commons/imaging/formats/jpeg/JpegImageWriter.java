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

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.common.Allocator;
import org.apache.commons.imaging.formats.jpeg.decoder.Dct;

public class JpegImageWriter {

    private static final int[] ZIG_ZAG = { 0, 1, 5, 6, 14, 15, 27, 28, 2, 4, 7, 13, 16, 26, 29, 42, 3, 8, 12, 17, 25, 30, 41, 43, 9, 11, 18, 24, 31, 40,
            44, 53, 10, 19, 23, 32, 39, 45, 52, 54, 20, 22, 33, 38, 46, 51, 55, 60, 21, 34, 37, 47, 50, 56, 59, 61, 35, 36, 48, 49, 57, 58, 62, 63 };

    private static final int[] STANDARD_LUMINANCE_QUANTIZATION_TABLE = { 16, 11, 10, 16, 24, 40, 51, 61, 12, 12, 14, 19, 26, 58, 60, 55, 14, 13, 16, 24, 40,
            57, 69, 56, 14, 17, 22, 29, 51, 87, 80, 62, 18, 22, 37, 56, 68, 109, 103, 77, 24, 35, 55, 64, 81, 104, 113, 92, 49, 64, 78, 87, 103, 121,
            120, 101, 72, 92, 95, 98, 112, 100, 103, 99 };

    private static final int[] STANDARD_CHROMINANCE_QUANTIZATION_TABLE = { 17, 18, 24, 47, 99, 99, 99, 99, 18, 21, 26, 66, 99, 99, 99, 99, 24, 26, 56, 99, 99,
            99, 99, 99, 47, 66, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99, 99,
            99, 99, 99, 99, 99, 99, 99, 99 };

    private static final byte[][] STANDARD_DC_LUMINANCE_BITS = { { 0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0 },
            { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 } };

    private static final byte[][] STANDARD_AC_LUMINANCE_BITS = { { 0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7d },
            { 0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12, 0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07, 0x22, 0x71, 0x14, 0x32, (byte) 0x81,
                    (byte) 0x91, (byte) 0xa1, 0x08, 0x23, 0x42, (byte) 0xb1, (byte) 0xc1, 0x15, 0x52, (byte) 0xd1, (byte) 0xf0, 0x24, 0x33, 0x62, 0x72,
                    (byte) 0x82, 0x09, 0x0a, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x25, 0x26, 0x27, 0x28, 0x29, 0x2a, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3a,
                    0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
                    0x69, 0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7a, (byte) 0x83, (byte) 0x84, (byte) 0x85, (byte) 0x86, (byte) 0x87,
                    (byte) 0x88, (byte) 0x89, (byte) 0x8a, (byte) 0x92, (byte) 0x93, (byte) 0x94, (byte) 0x95, (byte) 0x96, (byte) 0x97, (byte) 0x98,
                    (byte) 0x99, (byte) 0x9a, (byte) 0xa2, (byte) 0xa3, (byte) 0xa4, (byte) 0xa5, (byte) 0xa6, (byte) 0xa7, (byte) 0xa8, (byte) 0xa9,
                    (byte) 0xaa, (byte) 0xb2, (byte) 0xb3, (byte) 0xb4, (byte) 0xb5, (byte) 0xb6, (byte) 0xb7, (byte) 0xb8, (byte) 0xb9, (byte) 0xba,
                    (byte) 0xc2, (byte) 0xc3, (byte) 0xc4, (byte) 0xc5, (byte) 0xc6, (byte) 0xc7, (byte) 0xc8, (byte) 0xc9, (byte) 0xca, (byte) 0xd2,
                    (byte) 0xd3, (byte) 0xd4, (byte) 0xd5, (byte) 0xd6, (byte) 0xd7, (byte) 0xd8, (byte) 0xd9, (byte) 0xda, (byte) 0xe1, (byte) 0xe2,
                    (byte) 0xe3, (byte) 0xe4, (byte) 0xe5, (byte) 0xe6, (byte) 0xe7, (byte) 0xe8, (byte) 0xe9, (byte) 0xea, (byte) 0xf1, (byte) 0xf2,
                    (byte) 0xf3, (byte) 0xf4, (byte) 0xf5, (byte) 0xf6, (byte) 0xf7, (byte) 0xf8, (byte) 0xf9, (byte) 0xfa } };

    private static final byte[][] STANDARD_DC_CHROMINANCE_BITS = { { 0, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0 },
            { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 } };

    private static final byte[][] STANDARD_AC_CHROMINANCE_BITS = { { 0, 2, 1, 2, 4, 4, 3, 4, 7, 5, 4, 4, 0, 1, 2, 0x77 },
            { 0x00, 0x01, 0x02, 0x03, 0x11, 0x04, 0x05, 0x21, 0x31, 0x06, 0x12, 0x41, 0x51, 0x07, 0x61, 0x71, 0x13, 0x22, 0x32, (byte) 0x81, 0x08,
                    0x14, 0x42, (byte) 0x91, (byte) 0xa1, (byte) 0xb1, (byte) 0xc1, 0x09, 0x23, 0x33, 0x52, (byte) 0xf0, 0x15, 0x62, 0x72, (byte) 0xd1,
                    0x0a, 0x16, 0x24, 0x34, (byte) 0xe1, 0x25, (byte) 0xf1, 0x17, 0x18, 0x19, 0x1a, 0x26, 0x27, 0x28, 0x29, 0x2a, 0x35, 0x36, 0x37, 0x38,
                    0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5a, 0x63, 0x64, 0x65, 0x66,
                    0x67, 0x68, 0x69, 0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7a, (byte) 0x82, (byte) 0x83, (byte) 0x84, (byte) 0x85,
                    (byte) 0x86, (byte) 0x87, (byte) 0x88, (byte) 0x89, (byte) 0x8a, (byte) 0x92, (byte) 0x93, (byte) 0x94, (byte) 0x95, (byte) 0x96,
                    (byte) 0x97, (byte) 0x98, (byte) 0x99, (byte) 0x9a, (byte) 0xa2, (byte) 0xa3, (byte) 0xa4, (byte) 0xa5, (byte) 0xa6, (byte) 0xa7,
                    (byte) 0xa8, (byte) 0xa9, (byte) 0xaa, (byte) 0xb2, (byte) 0xb3, (byte) 0xb4, (byte) 0xb5, (byte) 0xb6, (byte) 0xb7, (byte) 0xb8,
                    (byte) 0xb9, (byte) 0xba, (byte) 0xc2, (byte) 0xc3, (byte) 0xc4, (byte) 0xc5, (byte) 0xc6, (byte) 0xc7, (byte) 0xc8, (byte) 0xc9,
                    (byte) 0xca, (byte) 0xd2, (byte) 0xd3, (byte) 0xd4, (byte) 0xd5, (byte) 0xd6, (byte) 0xd7, (byte) 0xd8, (byte) 0xd9, (byte) 0xda,
                    (byte) 0xe2, (byte) 0xe3, (byte) 0xe4, (byte) 0xe5, (byte) 0xe6, (byte) 0xe7, (byte) 0xe8, (byte) 0xe9, (byte) 0xea, (byte) 0xf2,
                    (byte) 0xf3, (byte) 0xf4, (byte) 0xf5, (byte) 0xf6, (byte) 0xf7, (byte) 0xf8, (byte) 0xf9, (byte) 0xfa } };

    public JpegImageWriter() {
    }

    public void writeImage(final BufferedImage src, final OutputStream os, final JpegImagingParameters params) throws ImagingException, IOException {
        final int quality = params != null ? params.getQuality() : JpegImagingParameters.DEFAULT_QUALITY;
        final String xmpXml = params != null ? params.getXmpXml() : null;
        final byte[] exifData = params != null ? params.getExifData() : null;
        final byte[] iccProfile = params != null ? params.getIccProfile() : null;

        final int width = src.getWidth();
        final int height = src.getHeight();

        final boolean isGrayscale = isGrayscale(src);

        final int[][] quantizationTables = generateQuantizationTables(quality, isGrayscale);

        os.write(0xff);
        os.write(0xd8);

        writeJfifSegment(os);

        if (exifData != null) {
            writeExifApp1Segment(os, exifData);
        }

        if (iccProfile != null) {
            writeIccProfileApp2Segment(os, iccProfile);
        }

        if (xmpXml != null) {
            writeXmpApp1Segment(os, xmpXml);
        }

        writeDqtSegment(os, quantizationTables, isGrayscale);

        writeSof0Segment(os, width, height, isGrayscale);

        writeDhtSegment(os, isGrayscale);

        writeSosSegment(os, isGrayscale);

        final int[] scanData = encodeScan(src, quantizationTables, isGrayscale);
        writeScanData(os, scanData);

        os.write(0xff);
        os.write(0xd9);
    }

    private boolean isGrayscale(final BufferedImage src) {
        final int type = src.getType();
        if (type == BufferedImage.TYPE_BYTE_GRAY) {
            return true;
        }
        for (int y = 0; y < Math.min(src.getHeight(), 8); y++) {
            for (int x = 0; x < Math.min(src.getWidth(), 8); x++) {
                final int argb = src.getRGB(x, y);
                final int r = argb >> 16 & 0xff;
                final int g = argb >> 8 & 0xff;
                final int b = argb & 0xff;
                if (r != g || g != b) {
                    return false;
                }
            }
        }
        return true;
    }

    private int[][] generateQuantizationTables(final int quality, final boolean isGrayscale) {
        final int scale;
        if (quality < 50) {
            scale = 5000 / quality;
        } else {
            scale = 200 - 2 * quality;
        }

        final int[] lumQt = Allocator.intArray(64);
        final int[] chromQt = Allocator.intArray(64);

        for (int i = 0; i < 64; i++) {
            int lumVal = (STANDARD_LUMINANCE_QUANTIZATION_TABLE[i] * scale + 50) / 100;
            if (lumVal < 1) {
                lumVal = 1;
            }
            if (lumVal > 255) {
                lumVal = 255;
            }
            lumQt[i] = lumVal;

            int chromVal = (STANDARD_CHROMINANCE_QUANTIZATION_TABLE[i] * scale + 50) / 100;
            if (chromVal < 1) {
                chromVal = 1;
            }
            if (chromVal > 255) {
                chromVal = 255;
            }
            chromQt[i] = chromVal;
        }

        if (isGrayscale) {
            return new int[][] { lumQt };
        }
        return new int[][] { lumQt, chromQt };
    }

    private void writeJfifSegment(final OutputStream os) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(JpegConstants.JFIF0_SIGNATURE.getBytes());
        baos.write(1);
        baos.write(2);
        baos.write(0);
        baos.write(0);
        baos.write(1);
        baos.write(0);
        baos.write(1);
        baos.write(0);
        baos.write(0);
        writeMarkerSegment(os, JpegConstants.JFIF_MARKER, baos.toByteArray());
    }

    private void writeXmpApp1Segment(final OutputStream os, final String xmpXml) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(JpegConstants.XMP_IDENTIFIER.getBytes());
        baos.write(xmpXml.getBytes(StandardCharsets.UTF_8));
        writeMarkerSegment(os, JpegConstants.JPEG_APP1_MARKER, baos.toByteArray());
    }

    void writeExifApp1Segment(final OutputStream os, final byte[] exifData) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(JpegConstants.EXIF_IDENTIFIER_CODE.getBytes());
        baos.write(0);
        baos.write(exifData);
        writeMarkerSegment(os, JpegConstants.JPEG_APP1_MARKER, baos.toByteArray());
    }

    void writeIccProfileApp2Segment(final OutputStream os, final byte[] iccProfile) throws IOException {
        final int maxChunkSize = 0xffff - 16;
        final int numChunks = (iccProfile.length + maxChunkSize - 1) / maxChunkSize;

        for (int chunk = 0; chunk < numChunks; chunk++) {
            final int start = chunk * maxChunkSize;
            final int end = Math.min(start + maxChunkSize, iccProfile.length);
            final int chunkLength = end - start;

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(JpegConstants.ICC_PROFILE_LABEL.getBytes());
            baos.write(chunk + 1);
            baos.write(numChunks);
            baos.write(iccProfile, start, chunkLength);
            writeMarkerSegment(os, JpegConstants.JPEG_APP2_MARKER, baos.toByteArray());
        }
    }

    private void writeDqtSegment(final OutputStream os, final int[][] quantizationTables, final boolean isGrayscale) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int t = 0; t < quantizationTables.length; t++) {
            baos.write(t);
            final int[] zigzagQt = Allocator.intArray(64);
            for (int i = 0; i < 64; i++) {
                zigzagQt[ZIG_ZAG[i]] = quantizationTables[t][i];
            }
            for (int i = 0; i < 64; i++) {
                baos.write(zigzagQt[i]);
            }
        }
        writeMarkerSegment(os, JpegConstants.DQT_MARKER, baos.toByteArray());
    }

    private void writeSof0Segment(final OutputStream os, final int width, final int height, final boolean isGrayscale) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(8);
        baos.write(height >> 8 & 0xff);
        baos.write(height & 0xff);
        baos.write(width >> 8 & 0xff);
        baos.write(width & 0xff);

        if (isGrayscale) {
            baos.write(1);
            baos.write(1);
            baos.write(0x11);
            baos.write(0);
        } else {
            baos.write(3);
            baos.write(1);
            baos.write(0x11);
            baos.write(0);
            baos.write(2);
            baos.write(0x11);
            baos.write(1);
            baos.write(3);
            baos.write(0x11);
            baos.write(1);
        }
        writeMarkerSegment(os, JpegConstants.SOF0_MARKER, baos.toByteArray());
    }

    private void writeDhtSegment(final OutputStream os, final boolean isGrayscale) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        writeHuffmanTableToStream(baos, 0x00, STANDARD_DC_LUMINANCE_BITS);
        writeHuffmanTableToStream(baos, 0x10, STANDARD_AC_LUMINANCE_BITS);

        if (!isGrayscale) {
            writeHuffmanTableToStream(baos, 0x01, STANDARD_DC_CHROMINANCE_BITS);
            writeHuffmanTableToStream(baos, 0x11, STANDARD_AC_CHROMINANCE_BITS);
        }

        writeMarkerSegment(os, JpegConstants.DHT_MARKER, baos.toByteArray());
    }

    private void writeHuffmanTableToStream(final ByteArrayOutputStream baos, final int tableClassAndId, final byte[][] table) {
        baos.write(tableClassAndId);
        for (int i = 0; i < 16; i++) {
            baos.write(table[0][i]);
        }
        for (int i = 0; i < table[1].length; i++) {
            baos.write(table[1][i]);
        }
    }

    private void writeSosSegment(final OutputStream os, final boolean isGrayscale) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        if (isGrayscale) {
            baos.write(1);
            baos.write(1);
            baos.write(0x00);
        } else {
            baos.write(3);
            baos.write(1);
            baos.write(0x00);
            baos.write(2);
            baos.write(0x11);
            baos.write(3);
            baos.write(0x11);
        }

        baos.write(0);
        baos.write(63);
        baos.write(0);

        writeMarkerSegment(os, JpegConstants.SOS_MARKER, baos.toByteArray());
    }

    private int[] encodeScan(final BufferedImage src, final int[][] quantizationTables, final boolean isGrayscale) {
        final int width = src.getWidth();
        final int height = src.getHeight();

        final int[] lumQt = quantizationTables[0];
        final int[] chromQt = isGrayscale ? null : quantizationTables[1];

        final HuffmanEncoder dcLumEncoder = new HuffmanEncoder(STANDARD_DC_LUMINANCE_BITS);
        final HuffmanEncoder acLumEncoder = new HuffmanEncoder(STANDARD_AC_LUMINANCE_BITS);
        final HuffmanEncoder dcChromEncoder = isGrayscale ? null : new HuffmanEncoder(STANDARD_DC_CHROMINANCE_BITS);
        final HuffmanEncoder acChromEncoder = isGrayscale ? null : new HuffmanEncoder(STANDARD_AC_CHROMINANCE_BITS);

        final int mcuWidth = 8;
        final int mcuHeight = 8;
        final int blocksX = (width + mcuWidth - 1) / mcuWidth;
        final int blocksY = (height + mcuHeight - 1) / mcuHeight;

        final int[] yBlock = Allocator.intArray(64);
        final int[] cbBlock = Allocator.intArray(64);
        final int[] crBlock = Allocator.intArray(64);
        final float[] floatBlock = new float[64];
        final int[] zigzagBlock = Allocator.intArray(64);

        final BitOutputStream bitOs = new BitOutputStream();

        int prevDcY = 0;
        int prevDcCb = 0;
        int prevDcCr = 0;

        for (int by = 0; by < blocksY; by++) {
            for (int bx = 0; bx < blocksX; bx++) {
                final int x0 = bx * mcuWidth;
                final int y0 = by * mcuHeight;

                if (isGrayscale) {
                    extractGrayBlock(src, x0, y0, width, height, yBlock);
                    forwardDctAndQuantize(yBlock, lumQt, floatBlock, zigzagBlock);
                    prevDcY = encodeBlock(zigzagBlock, dcLumEncoder, acLumEncoder, prevDcY, bitOs);
                } else {
                    extractYCbCrBlocks(src, x0, y0, width, height, yBlock, cbBlock, crBlock);

                    forwardDctAndQuantize(yBlock, lumQt, floatBlock, zigzagBlock);
                    prevDcY = encodeBlock(zigzagBlock, dcLumEncoder, acLumEncoder, prevDcY, bitOs);

                    forwardDctAndQuantize(cbBlock, chromQt, floatBlock, zigzagBlock);
                    prevDcCb = encodeBlock(zigzagBlock, dcChromEncoder, acChromEncoder, prevDcCb, bitOs);

                    forwardDctAndQuantize(crBlock, chromQt, floatBlock, zigzagBlock);
                    prevDcCr = encodeBlock(zigzagBlock, dcChromEncoder, acChromEncoder, prevDcCr, bitOs);
                }
            }
        }

        bitOs.flush();
        return bitOs.getData();
    }

    private void extractGrayBlock(final BufferedImage src, final int x0, final int y0, final int width, final int height, final int[] block) {
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                final int px = Math.min(x0 + x, width - 1);
                final int py = Math.min(y0 + y, height - 1);
                final int argb = src.getRGB(px, py);
                final int r = argb >> 16 & 0xff;
                final int g = argb >> 8 & 0xff;
                final int b = argb & 0xff;
                final int gray = (r + g + b) / 3;
                block[y * 8 + x] = gray - 128;
            }
        }
    }

    private void extractYCbCrBlocks(final BufferedImage src, final int x0, final int y0, final int width, final int height, final int[] yBlock,
            final int[] cbBlock, final int[] crBlock) {
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                final int px = Math.min(x0 + x, width - 1);
                final int py = Math.min(y0 + y, height - 1);
                final int argb = src.getRGB(px, py);
                final int r = argb >> 16 & 0xff;
                final int g = argb >> 8 & 0xff;
                final int b = argb & 0xff;

                final float yVal = 0.299f * r + 0.587f * g + 0.114f * b;
                final float cbVal = -0.168736f * r - 0.331264f * g + 0.5f * b + 128;
                final float crVal = 0.5f * r - 0.418688f * g - 0.081312f * b + 128;

                yBlock[y * 8 + x] = Math.round(yVal) - 128;
                cbBlock[y * 8 + x] = Math.round(cbVal) - 128;
                crBlock[y * 8 + x] = Math.round(crVal) - 128;
            }
        }
    }

    private void forwardDctAndQuantize(final int[] block, final int[] qt, final float[] floatBlock, final int[] zigzagBlock) {
        for (int i = 0; i < 64; i++) {
            floatBlock[i] = block[i];
        }

        Dct.forwardDct8x8(floatBlock);
        Dct.scaleQuantizationMatrix(floatBlock);

        final int[] quantized = Allocator.intArray(64);
        for (int i = 0; i < 64; i++) {
            quantized[i] = Math.round(floatBlock[i] / qt[i]);
        }

        for (int i = 0; i < 64; i++) {
            zigzagBlock[ZIG_ZAG[i]] = quantized[i];
        }
    }

    private int encodeBlock(final int[] zigzagBlock, final HuffmanEncoder dcEncoder, final HuffmanEncoder acEncoder, final int prevDc,
            final BitOutputStream bitOs) {
        final int dcDiff = zigzagBlock[0] - prevDc;
        encodeDc(dcDiff, dcEncoder, bitOs);

        encodeAc(zigzagBlock, acEncoder, bitOs);

        return zigzagBlock[0];
    }

    private void encodeDc(final int dcDiff, final HuffmanEncoder encoder, final BitOutputStream bitOs) {
        final int magnitude = Math.abs(dcDiff);
        final int category = magnitude == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(magnitude);

        encoder.encode(category, bitOs);

        if (category > 0) {
            final int bits;
            if (dcDiff < 0) {
                bits = dcDiff - 1;
            } else {
                bits = dcDiff;
            }
            bitOs.writeBits(bits, category);
        }
    }

    private void encodeAc(final int[] zigzagBlock, final HuffmanEncoder encoder, final BitOutputStream bitOs) {
        int zeroRun = 0;
        for (int i = 1; i < 64; i++) {
            if (zigzagBlock[i] == 0) {
                zeroRun++;
            } else {
                while (zeroRun >= 16) {
                    encoder.encode(0xf0, bitOs);
                    zeroRun -= 16;
                }

                final int magnitude = Math.abs(zigzagBlock[i]);
                final int category = 32 - Integer.numberOfLeadingZeros(magnitude);

                final int symbol = zeroRun << 4 | category;
                encoder.encode(symbol, bitOs);

                final int bits;
                if (zigzagBlock[i] < 0) {
                    bits = zigzagBlock[i] - 1;
                } else {
                    bits = zigzagBlock[i];
                }
                bitOs.writeBits(bits, category);

                zeroRun = 0;
            }
        }

        if (zeroRun > 0) {
            encoder.encode(0x00, bitOs);
        }
    }

    private void writeScanData(final OutputStream os, final int[] scanData) throws IOException {
        for (final int b : scanData) {
            os.write(b);
            if (b == 0xff) {
                os.write(0x00);
            }
        }
    }

    private void writeMarkerSegment(final OutputStream os, final int marker, final byte[] data) throws IOException {
        os.write(0xff);
        os.write(marker & 0xff);
        final int length = data.length + 2;
        os.write(length >> 8 & 0xff);
        os.write(length & 0xff);
        os.write(data);
    }

    private static final class HuffmanEncoder {
        private final int[] symbolToCode;
        private final int[] symbolToLength;

        HuffmanEncoder(final byte[][] table) {
            final int[] bits = new int[16];
            for (int i = 0; i < 16; i++) {
                bits[i] = table[0][i] & 0xff;
            }
            final byte[] values = table[1];

            int maxSymbol = 0;
            for (final byte v : values) {
                final int s = v & 0xff;
                if (s > maxSymbol) {
                    maxSymbol = s;
                }
            }

            symbolToCode = Allocator.intArray(maxSymbol + 1);
            symbolToLength = Allocator.intArray(maxSymbol + 1);

            int code = 0;
            int k = 0;
            for (int len = 1; len <= 16; len++) {
                for (int j = 0; j < bits[len - 1]; j++) {
                    if (k >= values.length) {
                        break;
                    }
                    final int symbol = values[k] & 0xff;
                    symbolToCode[symbol] = code;
                    symbolToLength[symbol] = len;
                    code++;
                    k++;
                }
                code <<= 1;
            }
        }

        void encode(final int symbol, final BitOutputStream bitOs) {
            if (symbol < 0 || symbol >= symbolToCode.length || symbolToLength[symbol] == 0) {
                return;
            }
            bitOs.writeBits(symbolToCode[symbol], symbolToLength[symbol]);
        }
    }

    private static final class BitOutputStream {
        private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        private int currentByte;
        private int bitsInCurrentByte;

        void writeBits(final int value, final int numBits) {
            for (int i = numBits - 1; i >= 0; i--) {
                final int bit = value >> i & 1;
                currentByte = currentByte << 1 | bit;
                bitsInCurrentByte++;
                if (bitsInCurrentByte == 8) {
                    baos.write(currentByte);
                    currentByte = 0;
                    bitsInCurrentByte = 0;
                }
            }
        }

        void flush() {
            if (bitsInCurrentByte > 0) {
                currentByte <<= 8 - bitsInCurrentByte;
                currentByte |= (1 << 8 - bitsInCurrentByte) - 1;
                baos.write(currentByte);
                currentByte = 0;
                bitsInCurrentByte = 0;
            }
        }

        int[] getData() {
            final byte[] bytes = baos.toByteArray();
            final int[] result = Allocator.intArray(bytes.length);
            for (int i = 0; i < bytes.length; i++) {
                result[i] = bytes[i] & 0xff;
            }
            return result;
        }
    }
}
