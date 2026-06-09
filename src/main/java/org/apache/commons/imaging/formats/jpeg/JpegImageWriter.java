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
import java.awt.image.ColorModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteOrder;

import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.common.ByteConversions;
import org.apache.commons.imaging.formats.jpeg.decoder.Dct;
import org.apache.commons.imaging.formats.jpeg.decoder.ZigZag;

/**
 * Writes baseline sequential 8-bit JPEG images with standard Huffman tables.
 * Supports RGB and grayscale color models. Quality is controlled via a 1-100 scale
 * that adjusts the quantization tables using the IJG formula.
 *
 * <p>The writer generates standard JPEG markers in the following order:</p>
 * <ol>
 *   <li>SOI (Start of Image)</li>
 *   <li>APP0 JFIF segment</li>
 *   <li>APP1 EXIF segment (if provided)</li>
 *   <li>APP2 ICC Profile segment (if provided)</li>
 *   <li>DQT (Define Quantization Tables)</li>
 *   <li>SOF0 (Start of Frame, Baseline DCT)</li>
 *   <li>DHT (Define Huffman Tables)</li>
 *   <li>SOS (Start of Scan)</li>
 *   <li>Entropy-coded image data</li>
 *   <li>EOI (End of Image)</li>
 * </ol>
 *
 * @since 1.0
 */
public class JpegImageWriter {

    private static final ByteOrder JPEG_BYTE_ORDER = ByteOrder.BIG_ENDIAN;

    private static final int[] STD_LUM_QUANT_TABLE = {
        16, 11, 10, 16, 24, 40, 51, 61,
        12, 12, 14, 19, 26, 58, 60, 55,
        14, 13, 16, 24, 40, 57, 69, 56,
        14, 17, 22, 29, 51, 87, 80, 62,
        18, 22, 37, 56, 68, 109, 103, 77,
        24, 35, 55, 64, 81, 104, 113, 92,
        49, 64, 78, 87, 103, 121, 120, 101,
        72, 92, 95, 98, 112, 100, 103, 99
    };

    private static final int[] STD_CHROM_QUANT_TABLE = {
        17, 18, 24, 47, 99, 99, 99, 99,
        18, 21, 26, 66, 99, 99, 99, 99,
        24, 26, 56, 99, 99, 99, 99, 99,
        47, 66, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99
    };

    private static final int[] DC_LUM_BITS = { 0, 0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0 };
    private static final int[] DC_LUM_VALUES = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };

    private static final int[] AC_LUM_BITS = { 0, 0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7d };
    private static final int[] AC_LUM_VALUES = {
        0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12,
        0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07,
        0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xa1, 0x08,
        0x23, 0x42, 0xb1, 0xc1, 0x15, 0x52, 0xd1, 0xf0,
        0x24, 0x33, 0x62, 0x72, 0x82, 0x09, 0x0a, 0x16,
        0x17, 0x18, 0x19, 0x1a, 0x25, 0x26, 0x27, 0x28,
        0x29, 0x2a, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39,
        0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
        0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59,
        0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69,
        0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79,
        0x7a, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89,
        0x8a, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98,
        0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7,
        0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6,
        0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3, 0xc4, 0xc5,
        0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2, 0xd3, 0xd4,
        0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda, 0xe1, 0xe2,
        0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea,
        0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8,
        0xf9, 0xfa
    };

    private static final int[] DC_CHROM_BITS = { 0, 0, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0 };
    private static final int[] DC_CHROM_VALUES = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };

    private static final int[] AC_CHROM_BITS = { 0, 0, 2, 1, 2, 4, 4, 3, 4, 7, 5, 4, 4, 0, 1, 2, 0x77 };
    private static final int[] AC_CHROM_VALUES = {
        0x00, 0x01, 0x02, 0x03, 0x11, 0x04, 0x05, 0x21,
        0x31, 0x06, 0x12, 0x41, 0x51, 0x07, 0x61, 0x71,
        0x13, 0x22, 0x32, 0x81, 0x08, 0x14, 0x42, 0x91,
        0xa1, 0xb1, 0xc1, 0x09, 0x23, 0x33, 0x52, 0xf0,
        0x15, 0x62, 0x72, 0xd1, 0x0a, 0x16, 0x24, 0x34,
        0xe1, 0x25, 0xf1, 0x17, 0x18, 0x19, 0x1a, 0x26,
        0x27, 0x28, 0x29, 0x2a, 0x35, 0x36, 0x37, 0x38,
        0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48,
        0x49, 0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58,
        0x59, 0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
        0x69, 0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78,
        0x79, 0x7a, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87,
        0x88, 0x89, 0x8a, 0x92, 0x93, 0x94, 0x95, 0x96,
        0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5,
        0xa6, 0xa7, 0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4,
        0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3,
        0xc4, 0xc5, 0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2,
        0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda,
        0xe2, 0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9,
        0xea, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8,
        0xf9, 0xfa
    };

    private static final int[] zigzagInverse = computeZigZagInverse();

    private static int[] computeZigZagInverse() {
        final int[] result = new int[64];
        for (int i = 0; i < 64; i++) {
            result[ZigZag.ZIG_ZAG[i]] = i;
        }
        return result;
    }

    private static int[] buildScaledQuantizationTable(final int[] baseTable, final int quality) {
        final int[] result = new int[64];
        final float scale;
        if (quality < 50) {
            scale = 5000.0f / quality;
        } else {
            scale = 200.0f - 2.0f * quality;
        }
        for (int i = 0; i < 64; i++) {
            int q = (int) (baseTable[i] * scale / 100.0f + 0.5f);
            if (q < 1) {
                q = 1;
            }
            if (q > 255) {
                q = 255;
            }
            result[i] = q;
        }
        return result;
    }

    private static final class HuffmanEncoder {
        private final int[] ehufco = new int[256];
        private final int[] ehufsi = new int[256];

        HuffmanEncoder(final int[] bits, final int[] values) {
            final int[] huffsize = new int[257];
            int k = 0;
            for (int i = 1; i <= 16; i++) {
                for (int j = 0; j < bits[i]; j++) {
                    huffsize[k] = i;
                    k++;
                }
            }
            huffsize[k] = 0;

            final int[] huffcode = new int[257];
            k = 0;
            int code = 0;
            int si = huffsize[0];
            while (huffsize[k] != 0) {
                while (huffsize[k] == si) {
                    huffcode[k] = code;
                    code++;
                    k++;
                }
                code <<= 1;
                si++;
            }

            for (k = 0; k < values.length; k++) {
                final int idx = values[k];
                ehufco[idx] = huffcode[k];
                ehufsi[idx] = huffsize[k];
            }
        }

        void encode(final int value, final BitOutputStream bos) throws IOException {
            if (value == 0) {
                bos.writeBits(ehufco[0], ehufsi[0]);
                return;
            }

            int absv = value < 0 ? -value : value;
            int ssss = 0;
            while (absv != 0) {
                ssss++;
                absv >>= 1;
            }

            bos.writeBits(ehufco[ssss], ehufsi[ssss]);

            if (value < 0) {
                bos.writeBits(value - 1, ssss);
            } else {
                bos.writeBits(value, ssss);
            }
        }

        void encodeAc(final int run, final int value, final BitOutputStream bos) throws IOException {
            if (value == 0) {
                bos.writeBits(ehufco[0], ehufsi[0]);
                return;
            }

            int absv = value < 0 ? -value : value;
            int ssss = 0;
            while (absv != 0) {
                ssss++;
                absv >>= 1;
            }

            final int rs = (run << 4) | ssss;
            bos.writeBits(ehufco[rs], ehufsi[rs]);

            if (value < 0) {
                bos.writeBits(value - 1, ssss);
            } else {
                bos.writeBits(value, ssss);
            }
        }
    }

    private static final class BitOutputStream {
        private final OutputStream os;
        private int bitBuffer;
        private int bitCount;

        BitOutputStream(final OutputStream os) {
            this.os = os;
        }

        void writeBits(int value, int numBits) throws IOException {
            if (numBits == 0) {
                return;
            }
            final int mask = (1 << numBits) - 1;
            value &= mask;

            while (numBits > 0) {
                final int remaining = 8 - bitCount;
                if (numBits >= remaining) {
                    bitBuffer = (bitBuffer << remaining) | (value >> (numBits - remaining));
                    bitCount = 8;
                    os.write(bitBuffer);
                    if (bitBuffer == 0xff) {
                        os.write(0x00);
                    }
                    bitBuffer = 0;
                    bitCount = 0;
                    numBits -= remaining;
                    value &= (1 << numBits) - 1;
                } else {
                    bitBuffer = (bitBuffer << numBits) | value;
                    bitCount += numBits;
                    numBits = 0;
                }
            }
        }

        void flush() throws IOException {
            if (bitCount > 0) {
                bitBuffer <<= (8 - bitCount);
                os.write(bitBuffer);
                if (bitBuffer == 0xff) {
                    os.write(0x00);
                }
                bitBuffer = 0;
                bitCount = 0;
            }
        }
    }

    private static void writeMarker(final OutputStream os, final int marker) throws IOException {
        os.write(0xff);
        os.write(marker & 0xff);
    }

    private static void writeShort(final OutputStream os, final int value) throws IOException {
        os.write(ByteConversions.toBytes((short) value, JPEG_BYTE_ORDER));
    }

    private static void writeQuantizationTable(final OutputStream os, final int[] quantTable, final int tableId) throws IOException {
        writeMarker(os, JpegConstants.DQT_MARKER & 0xff);
        writeShort(os, 67);
        os.write(tableId);
        for (int i = 0; i < 64; i++) {
            final int rasterIndex = zigzagInverse[i];
            os.write(quantTable[rasterIndex]);
        }
    }

    private static void writeHuffmanTable(final OutputStream os, final int[] bits, final int[] values, final int tableClass,
            final int tableId) throws IOException {
        writeMarker(os, JpegConstants.DHT_MARKER & 0xff);
        final int huffvalCount = values.length;
        final int segmentLength = 2 + 1 + 16 + huffvalCount;
        writeShort(os, segmentLength);
        os.write((tableClass << 4) | tableId);
        for (int i = 1; i <= 16; i++) {
            os.write(bits[i]);
        }
        for (final int value : values) {
            os.write(value);
        }
    }

    private static void writeJfifSegment(final OutputStream os) throws IOException {
        writeMarker(os, JpegConstants.JFIF_MARKER & 0xff);
        writeShort(os, 16);
        os.write(0x4a); // J
        os.write(0x46); // F
        os.write(0x49); // I
        os.write(0x46); // F
        os.write(0x00); // null terminator
        writeShort(os, 0x0102); // version 1.2
        os.write(0); // density units: pixel aspect ratio
        writeShort(os, 1); // X density
        writeShort(os, 1); // Y density
        os.write(0); // thumbnail width
        os.write(0); // thumbnail height
    }

    private static boolean isGrayscale(final BufferedImage src) {
        final ColorModel cm = src.getColorModel();
        if (cm.getNumColorComponents() == 1) {
            return true;
        }
        final int type = src.getType();
        return type == BufferedImage.TYPE_BYTE_GRAY || type == BufferedImage.TYPE_USHORT_GRAY;
    }

    /**
     * Writes a BufferedImage as a baseline sequential JPEG to the output stream.
     *
     * @param src    the source BufferedImage
     * @param os     the output stream to write to
     * @param params the JPEG imaging parameters (may be null)
     * @throws ImagingException if the image cannot be encoded
     * @throws IOException      if an I/O error occurs
     */
    public void writeImage(final BufferedImage src, final OutputStream os, final JpegImagingParameters params)
            throws ImagingException, IOException {
        final int width = src.getWidth();
        final int height = src.getHeight();
        final int quality = params != null ? params.getCompressionQuality() : 85;

        final boolean grayscale = isGrayscale(src);
        final int numComponents = grayscale ? 1 : 3;

        final int[] yBand;
        final int[] cbBand;
        final int[] crBand;
        final int[][] samples;

        if (grayscale) {
            yBand = new int[width * height];
            cbBand = null;
            crBand = null;
            samples = new int[][] { yBand };
            final int[] rgb = src.getRGB(0, 0, width, height, null, 0, width);
            for (int i = 0; i < width * height; i++) {
                final int r = (rgb[i] >> 16) & 0xff;
                final int g = (rgb[i] >> 8) & 0xff;
                final int b = rgb[i] & 0xff;
                yBand[i] = clamp((int) (0.299 * r + 0.587 * g + 0.114 * b));
            }
        } else {
            yBand = new int[width * height];
            cbBand = new int[width * height];
            crBand = new int[width * height];
            samples = new int[][] { yBand, cbBand, crBand };
            final int[] rgb = src.getRGB(0, 0, width, height, null, 0, width);
            for (int i = 0; i < width * height; i++) {
                final int r = (rgb[i] >> 16) & 0xff;
                final int g = (rgb[i] >> 8) & 0xff;
                final int b = rgb[i] & 0xff;
                yBand[i] = clamp((int) (0.299 * r + 0.587 * g + 0.114 * b));
                cbBand[i] = clamp((int) (128.0 - 0.168736 * r - 0.331264 * g + 0.5 * b));
                crBand[i] = clamp((int) (128.0 + 0.5 * r - 0.418688 * g - 0.081312 * b));
            }
        }

        final int[] lumQuantTable = buildScaledQuantizationTable(STD_LUM_QUANT_TABLE, quality);
        final int[] chromQuantTable = buildScaledQuantizationTable(STD_CHROM_QUANT_TABLE, quality);

        final ByteArrayOutputStream scanData = new ByteArrayOutputStream();
        final BitOutputStream bos = new BitOutputStream(scanData);

        final HuffmanEncoder dcLumEnc = new HuffmanEncoder(DC_LUM_BITS, DC_LUM_VALUES);
        final HuffmanEncoder acLumEnc = new HuffmanEncoder(AC_LUM_BITS, AC_LUM_VALUES);
        final HuffmanEncoder dcChromEnc = new HuffmanEncoder(DC_CHROM_BITS, DC_CHROM_VALUES);
        final HuffmanEncoder acChromEnc = new HuffmanEncoder(AC_CHROM_BITS, AC_CHROM_VALUES);

        final int[] preds = new int[numComponents];
        final float[] block = new float[64];

        for (int y = 0; y < height; y += 8) {
            for (int x = 0; x < width; x += 8) {
                for (int comp = 0; comp < numComponents; comp++) {
                    final int[] compSamples = samples[comp];
                    final int[] compQuant;
                    final HuffmanEncoder dcEnc;
                    final HuffmanEncoder acEnc;
                    if (grayscale || comp == 0) {
                        compQuant = lumQuantTable;
                        dcEnc = dcLumEnc;
                        acEnc = acLumEnc;
                    } else {
                        compQuant = chromQuantTable;
                        dcEnc = dcChromEnc;
                        acEnc = acChromEnc;
                    }

                    for (int yy = 0; yy < 8; yy++) {
                        for (int xx = 0; xx < 8; xx++) {
                            int px = x + xx;
                            int py = y + yy;
                            if (px >= width) {
                                px = width - 1;
                            }
                            if (py >= height) {
                                py = height - 1;
                            }
                            final int sample = compSamples[py * width + px];
                            block[yy * 8 + xx] = sample - 128.0f;
                        }
                    }

                    Dct.forwardDct8x8(block);

                    for (int i = 0; i < 64; i++) {
                        final int row = i / 8;
                        final int col = i % 8;
                        final float scale = Dct.DCT_SCALING_FACTORS[row] * Dct.DCT_SCALING_FACTORS[col];
                        final double qval = (double) block[i] * scale / (double) compQuant[i];
                        block[i] = (float) (qval >= 0 ? (int) (qval + 0.5) : (int) (qval - 0.5));
                    }

                    final int dc = (int) block[0];
                    final int diff = dc - preds[comp];
                    preds[comp] = dc;
                    dcEnc.encode(diff, bos);

                    final int[] zz = new int[64];
                    for (int i = 0; i < 64; i++) {
                        zz[ZigZag.ZIG_ZAG[i]] = (int) block[i];
                    }

                    int run = 0;
                    for (int i = 1; i < 64; i++) {
                        if (zz[i] == 0) {
                            run++;
                            if (i == 63) {
                                acEnc.encodeAc(0, 0, bos);
                            }
                        } else {
                            while (run >= 16) {
                                acEnc.encodeAc(15, 0, bos);
                                run -= 16;
                            }
                            acEnc.encodeAc(run, zz[i], bos);
                            run = 0;
                        }
                    }
                }
            }
        }

        bos.flush();
        final byte[] scanBytes = scanData.toByteArray();

        os.write(JpegConstants.SOI.get(0) & 0xff);
        os.write(JpegConstants.SOI.get(1) & 0xff);

        writeJfifSegment(os);

        if (params != null && params.getExifData() != null) {
            final byte[] exif = params.getExifData();
            writeMarker(os, JpegConstants.JPEG_APP1_MARKER & 0xff);
            writeShort(os, exif.length + 8);
            os.write(0x45); os.write(0x78); os.write(0x69); os.write(0x66);
            os.write(0x00); os.write(0x00);
            os.write(exif);
        }

        if (params != null && params.getIccProfileData() != null) {
            final byte[] icc = params.getIccProfileData();
            final int iccHeaderLen = 14;
            final int maxDataLen = 65535 - 2 - iccHeaderLen;
            final int totalChunks = (icc.length + maxDataLen - 1) / maxDataLen;
            for (int chunk = 0; chunk < totalChunks; chunk++) {
                final int offset = chunk * maxDataLen;
                final int chunkLen = Math.min(maxDataLen, icc.length - offset);
                writeMarker(os, JpegConstants.JPEG_APP2_MARKER & 0xff);
                writeShort(os, 2 + iccHeaderLen + chunkLen);
                os.write(0x49); os.write(0x43); os.write(0x43); os.write(0x5F); // "ICC_"
                os.write(0x50); os.write(0x52); os.write(0x4F); os.write(0x46); // "PROF"
                os.write(0x49); os.write(0x4C); os.write(0x45); // "ILE"
                os.write(0); // null terminator
                os.write(chunk + 1);
                os.write(totalChunks);
                os.write(icc, offset, chunkLen);
            }
        }

        if (grayscale) {
            writeQuantizationTable(os, lumQuantTable, 0);
        } else {
            writeQuantizationTable(os, lumQuantTable, 0);
            writeQuantizationTable(os, chromQuantTable, 1);
        }

        final int sofnMarker = JpegConstants.SOF0_MARKER & 0xff;
        writeMarker(os, sofnMarker);
        final int sofnLen = 8 + 3 * numComponents;
        writeShort(os, sofnLen);
        os.write(8);
        writeShort(os, height);
        writeShort(os, width);
        os.write(numComponents);
        for (int comp = 0; comp < numComponents; comp++) {
            os.write(comp + 1);
            os.write(0x11);
            os.write(comp == 0 ? 0 : 1);
        }

        if (grayscale) {
            writeHuffmanTable(os, DC_LUM_BITS, DC_LUM_VALUES, 0, 0);
            writeHuffmanTable(os, AC_LUM_BITS, AC_LUM_VALUES, 1, 0);
        } else {
            writeHuffmanTable(os, DC_LUM_BITS, DC_LUM_VALUES, 0, 0);
            writeHuffmanTable(os, AC_LUM_BITS, AC_LUM_VALUES, 1, 0);
            writeHuffmanTable(os, DC_CHROM_BITS, DC_CHROM_VALUES, 0, 1);
            writeHuffmanTable(os, AC_CHROM_BITS, AC_CHROM_VALUES, 1, 1);
        }

        final int sosMarker = JpegConstants.SOS_MARKER & 0xff;
        writeMarker(os, sosMarker);
        writeShort(os, 6 + 2 * numComponents);
        os.write(numComponents);
        for (int comp = 0; comp < numComponents; comp++) {
            os.write(comp + 1);
            if (comp == 0) {
                os.write(0x00);
            } else {
                os.write(0x11);
            }
        }
        os.write(0);
        os.write(63);
        os.write(0);

        os.write(scanBytes);

        os.write(JpegConstants.EOI.get(0) & 0xff);
        os.write(JpegConstants.EOI.get(1) & 0xff);
    }

    private static int clamp(final int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 255) {
            return 255;
        }
        return value;
    }
}