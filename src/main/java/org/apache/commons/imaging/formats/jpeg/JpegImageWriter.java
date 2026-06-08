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

import static org.apache.commons.imaging.common.BinaryFunctions.write2Bytes;
import static org.apache.commons.imaging.common.BinaryFunctions.writeByte;

import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteOrder;

import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.common.Allocator;

/**
 * JPEG image writer.
 *
 * @since 1.0-alpha5
 */
public class JpegImageWriter {

    private static final int[] ZIGZAG = {
        0, 1, 5, 6, 14, 15, 27, 28,
        2, 4, 7, 13, 16, 26, 29, 42,
        3, 8, 12, 17, 25, 30, 41, 43,
        9, 11, 18, 24, 31, 40, 44, 53,
        10, 19, 23, 32, 39, 45, 52, 54,
        20, 22, 33, 38, 46, 51, 55, 60,
        21, 34, 37, 47, 50, 56, 59, 61,
        35, 36, 48, 49, 57, 58, 62, 63
    };

    private static final int[] STD_LUMINANCE_QUANT = {
        16, 11, 10, 16, 24, 40, 51, 61,
        12, 12, 14, 19, 26, 58, 60, 55,
        14, 13, 16, 24, 40, 57, 69, 56,
        14, 17, 22, 29, 51, 87, 80, 62,
        18, 22, 37, 56, 68, 109, 103, 77,
        24, 35, 55, 64, 81, 104, 113, 92,
        49, 64, 78, 87, 103, 121, 120, 101,
        72, 92, 95, 98, 112, 100, 103, 99
    };

    private static final int[] STD_CHROMINANCE_QUANT = {
        17, 18, 24, 47, 99, 99, 99, 99,
        18, 21, 26, 66, 99, 99, 99, 99,
        24, 26, 56, 99, 99, 99, 99, 99,
        47, 66, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99
    };

    private static final int[] STD_DC_LUMINANCE_LENGTHS = {
        0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0
    };

    private static final int[] STD_DC_LUMINANCE_VALUES = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11
    };

    private static final int[] STD_AC_LUMINANCE_LENGTHS = {
        0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 0x7d
    };

    private static final int[] STD_AC_LUMINANCE_VALUES = {
        0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12, 0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61,
        0x07, 0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xa1, 0x08, 0x23, 0x42, 0xb1, 0xc1, 0x15,
        0x52, 0xd1, 0xf0, 0x24, 0x33, 0x62, 0x72, 0x82, 0x09, 0x0a, 0x16, 0x17, 0x18, 0x19,
        0x1a, 0x25, 0x26, 0x27, 0x28, 0x29, 0x2a, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3a,
        0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58,
        0x59, 0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6a, 0x73, 0x74, 0x75, 0x76,
        0x77, 0x78, 0x79, 0x7a, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89, 0x8a, 0x92, 0x93,
        0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7, 0xa8,
        0xa9, 0xaa, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3, 0xc4,
        0xc5, 0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9,
        0xda, 0xe1, 0xe2, 0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea, 0xf1, 0xf2, 0xf3,
        0xf4, 0xf5, 0xf6, 0xf7, 0xf8, 0xf9, 0xfa
    };

    private static final int[] STD_DC_CHROMINANCE_LENGTHS = {
        0, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0
    };

    private static final int[] STD_DC_CHROMINANCE_VALUES = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11
    };

    private static final int[] STD_AC_CHROMINANCE_LENGTHS = {
        0, 2, 1, 2, 4, 4, 3, 4, 7, 5, 4, 4, 0, 1, 2, 0x77
    };

    private static final int[] STD_AC_CHROMINANCE_VALUES = {
        0x00, 0x01, 0x02, 0x03, 0x11, 0x04, 0x05, 0x21, 0x31, 0x06, 0x12, 0x41, 0x51, 0x07,
        0x61, 0x71, 0x13, 0x22, 0x32, 0x81, 0x08, 0x14, 0x42, 0x91, 0xa1, 0xb1, 0xc1, 0x09,
        0x23, 0x33, 0x52, 0xf0, 0x15, 0x62, 0x72, 0xd1, 0x0a, 0x16, 0x24, 0x34, 0xe1, 0x25,
        0xf1, 0x17, 0x18, 0x19, 0x1a, 0x26, 0x27, 0x28, 0x29, 0x2a, 0x35, 0x36, 0x37, 0x38,
        0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4a, 0x53, 0x54, 0x55, 0x56,
        0x57, 0x58, 0x59, 0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6a, 0x73, 0x74,
        0x75, 0x76, 0x77, 0x78, 0x79, 0x7a, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89,
        0x8a, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5,
        0xa6, 0xa7, 0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba,
        0xc2, 0xc3, 0xc4, 0xc5, 0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2, 0xd3, 0xd4, 0xd5, 0xd6,
        0xd7, 0xd8, 0xd9, 0xda, 0xe2, 0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea, 0xf2,
        0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8, 0xf9, 0xfa
    };

    private static class BitOutputStream {
        private final OutputStream os;
        private int buffer = 0;
        private int bitsInBuffer = 0;

        BitOutputStream(final OutputStream os) {
            this.os = os;
        }

        void writeBits(final int bits, final int numBits) throws IOException {
            buffer <<= numBits;
            buffer |= bits & ((1 << numBits) - 1);
            bitsInBuffer += numBits;

            while (bitsInBuffer >= 8) {
                bitsInBuffer -= 8;
                int byteToWrite = (buffer >> bitsInBuffer) & 0xff;
                os.write(byteToWrite);
                if (byteToWrite == 0xff) {
                    os.write(0x00);
                }
                buffer &= (1 << bitsInBuffer) - 1;
            }
        }

        void flush() throws IOException {
            if (bitsInBuffer > 0) {
                writeBits(0, 8 - bitsInBuffer);
            }
        }
    }

    private static class HuffmanEncoder {
        private final int[] codes;
        private final int[] lengths;

        HuffmanEncoder(final int[] bits, final int[] values) {
            codes = new int[256];
            lengths = new int[256];

            int code = 0;
            int k = 0;
            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < bits[i]; j++) {
                    codes[values[k]] = code;
                    lengths[values[k]] = i + 1;
                    code++;
                    k++;
                }
                code <<= 1;
            }
        }

        int getCode(final int value) {
            return codes[value];
        }

        int getLength(final int value) {
            return lengths[value];
        }
    }

    private final float quality;

    public JpegImageWriter(final float quality) {
        this.quality = quality;
    }

    public void writeImage(final BufferedImage src, final OutputStream os) throws ImagingException, IOException {
        writeImage(src, os, null, null);
    }

    public void writeImage(final BufferedImage src, final OutputStream os,
                           final byte[] exifData, final byte[] iccProfileData)
            throws ImagingException, IOException {
        final int width = src.getWidth();
        final int height = src.getHeight();

        final int[] rgbPixels = new int[width * height];
        src.getRGB(0, 0, width, height, rgbPixels, 0, width);

        final float[][][] yCbCrBlocks = convertToYCbCrBlocks(rgbPixels, width, height);

        final int[] luminanceQuant = createQuantizationTable(STD_LUMINANCE_QUANT, quality);
        final int[] chrominanceQuant = createQuantizationTable(STD_CHROMINANCE_QUANT, quality);

        final float[][][] dctCoeffs = performDCT(yCbCrBlocks, luminanceQuant, chrominanceQuant);

        final BitOutputStream bos = new BitOutputStream(os);

        os.write(0xff);
        os.write(0xd8);

        writeJFIFSegment(os);

        if (exifData != null) {
            writeApp1Segment(os, exifData);
        }

        if (iccProfileData != null) {
            writeApp2Segments(os, iccProfileData);
        }

        writeDQT(os, luminanceQuant, chrominanceQuant);

        writeSOF0(os, width, height);

        writeDHT(os);

        writeSOS(os);

        encodeImageData(dctCoeffs, bos, width, height);

        bos.flush();

        os.write(0xff);
        os.write(0xd9);
    }

    private float[][][] convertToYCbCrBlocks(final int[] pixels, final int width, final int height) {
        final int mcuWidth = (width + 7) / 8;
        final int mcuHeight = (height + 7) / 8;

        final float[][][] blocks = new float[3][mcuWidth * mcuHeight][64];

        for (int mcuY = 0; mcuY < mcuHeight; mcuY++) {
            for (int mcuX = 0; mcuX < mcuWidth; mcuX++) {
                final int mcuIndex = mcuY * mcuWidth + mcuX;
                final int yStart = mcuY * 8;
                final int xStart = mcuX * 8;

                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        final int px = xStart + x;
                        final int py = yStart + y;

                        if (px < width && py < height) {
                            final int pixel = pixels[py * width + px];
                            final int r = (pixel >> 16) & 0xff;
                            final int g = (pixel >> 8) & 0xff;
                            final int b = pixel & 0xff;

                            final float yVal = 0.299f * r + 0.587f * g + 0.114f * b - 128f;
                            final float cb = -0.1687f * r - 0.3313f * g + 0.5f * b;
                            final float cr = 0.5f * r - 0.4187f * g - 0.0813f * b;

                            blocks[0][mcuIndex][y * 8 + x] = yVal;
                            blocks[1][mcuIndex][y * 8 + x] = cb;
                            blocks[2][mcuIndex][y * 8 + x] = cr;
                        } else {
                            blocks[0][mcuIndex][y * 8 + x] = 0;
                            blocks[1][mcuIndex][y * 8 + x] = 0;
                            blocks[2][mcuIndex][y * 8 + x] = 0;
                        }
                    }
                }
            }
        }

        return blocks;
    }

    private int[] createQuantizationTable(final int[] baseTable, final float quality) {
        final float scale;
        if (quality <= 0) {
            scale = 50f;
        } else if (quality < 50) {
            scale = 5000f / quality;
        } else {
            scale = 200 - quality * 2;
        }

        final int[] result = Allocator.intArray(64);
        for (int i = 0; i < 64; i++) {
            int q = (int) ((baseTable[i] * scale + 50) / 100);
            if (q <= 0) {
                q = 1;
            } else if (q > 255) {
                q = 255;
            }
            result[i] = q;
        }
        return result;
    }

    private float[][][] performDCT(final float[][][] yCbCrBlocks,
                                    final int[] luminanceQuant, final int[] chrominanceQuant) {
        final int numBlocks = yCbCrBlocks[0].length;
        final float[][][] dctBlocks = new float[3][numBlocks][64];

        for (int c = 0; c < 3; c++) {
            final int[] quant = c == 0 ? luminanceQuant : chrominanceQuant;
            for (int b = 0; b < numBlocks; b++) {
                final float[] block = yCbCrBlocks[c][b];
                final float[] dct = fdct(block);
                for (int i = 0; i < 64; i++) {
                    dctBlocks[c][b][ZIGZAG[i]] = Math.round(dct[i] / quant[i]);
                }
            }
        }
        return dctBlocks;
    }

    private float[] fdct(final float[] block) {
        final float[] fdctData = Allocator.floatArray(64);
        final float[] tmp = Allocator.floatArray(64);

        for (int i = 0; i < 8; i++) {
            final float[] row = new float[8];
            for (int j = 0; j < 8; j++) {
                row[j] = block[i * 8 + j];
            }
            final float[] dctRow = dct1d(row);
            for (int j = 0; j < 8; j++) {
                tmp[i * 8 + j] = dctRow[j];
            }
        }

        for (int j = 0; j < 8; j++) {
            final float[] col = new float[8];
            for (int i = 0; i < 8; i++) {
                col[i] = tmp[i * 8 + j];
            }
            final float[] dctCol = dct1d(col);
            for (int i = 0; i < 8; i++) {
                fdctData[i * 8 + j] = dctCol[i];
            }
        }

        return fdctData;
    }

    private float[] dct1d(final float[] data) {
        final float[] dct = new float[8];
        final float[] tmp = new float[8];

        for (int i = 0; i < 4; i++) {
            final float x0 = data[i];
            final float x1 = data[7 - i];
            tmp[i] = x0 + x1;
            tmp[7 - i] = x0 - x1;
        }

        final float x0 = tmp[0];
        final float x1 = tmp[1];
        final float x2 = tmp[2];
        final float x3 = tmp[3];
        tmp[0] = x0 + x3;
        tmp[3] = x0 - x3;
        tmp[1] = x1 + x2;
        tmp[2] = x1 - x2;

        dct[0] = (tmp[0] + tmp[1]) * 0.5f;
        dct[4] = (tmp[0] - tmp[1]) * 0.5f;

        final float c2 = 0.541196100f;
        final float c4 = 0.707106781f;
        final float c6 = 1.306562965f;
        dct[2] = tmp[2] * c2 + tmp[3] * c6;
        dct[6] = tmp[3] * c2 - tmp[2] * c6;

        final float x4 = tmp[4];
        final float x5 = tmp[5];
        final float x6 = tmp[6];
        final float x7 = tmp[7];

        final float tmp3 = x4 + x6;
        final float tmp4 = x5 + x7;
        final float tmp5 = x4 - x6;
        final float tmp6 = x5 - x7;

        dct[7] = (tmp5 - tmp6) * c4;
        dct[1] = (tmp3 + tmp4) * 0.5f;
        dct[5] = (tmp3 - tmp4) * 0.5f;

        final float c1 = 0.382683433f;
        final float c3 = 0.923879533f;
        final float c5 = 1.175570505f;
        final float c7 = 0.195090322f;
        final float tmp1 = tmp6 * c1 + tmp5 * c7;
        final float tmp2 = tmp5 * c3 - tmp6 * c5;
        dct[3] = tmp1 + tmp2;
        dct[7] = tmp2 - tmp1;

        return dct;
    }

    private void writeJFIFSegment(final OutputStream os) throws IOException {
        os.write(0xff);
        os.write(0xe0);
        final byte[] jfifData = {
            'J', 'F', 'I', 'F', 0,
            1, 1, 0, 0, 1, 0, 1, 0, 0
        };
        write2Bytes(ByteOrder.BIG_ENDIAN, jfifData.length + 2, os);
        os.write(jfifData);
    }

    private void writeApp1Segment(final OutputStream os, final byte[] exifData) throws IOException {
        os.write(0xff);
        os.write(0xe1);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write('E');
        baos.write('x');
        baos.write('i');
        baos.write('f');
        baos.write(0);
        baos.write(0);
        baos.write(exifData);
        write2Bytes(ByteOrder.BIG_ENDIAN, baos.size() + 2, os);
        baos.writeTo(os);
    }

    private void writeApp2Segments(final OutputStream os, final byte[] iccProfileData) throws IOException {
        int remaining = iccProfileData.length;
        int offset = 0;
        int seq = 1;
        int numMarkers = (remaining + 0xffd5 - 1) / 0xffd5;
        while (remaining > 0) {
            os.write(0xff);
            os.write(0xe2);
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write('I');
            baos.write('C');
            baos.write('C');
            baos.write('_');
            baos.write('P');
            baos.write('R');
            baos.write('O');
            baos.write('F');
            baos.write('I');
            baos.write('L');
            baos.write('E');
            baos.write(0);
            baos.write(seq);
            baos.write(numMarkers);
            final int chunkSize = Math.min(remaining, 0xffd5 - 16);
            baos.write(iccProfileData, offset, chunkSize);
            write2Bytes(ByteOrder.BIG_ENDIAN, baos.size() + 2, os);
            baos.writeTo(os);
            offset += chunkSize;
            remaining -= chunkSize;
            seq++;
        }
    }

    private void writeDQT(final OutputStream os, final int[] luminanceQuant, final int[] chrominanceQuant)
            throws IOException {
        os.write(0xff);
        os.write(0xdb);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0);
        for (int i = 0; i < 64; i++) {
            baos.write(luminanceQuant[ZIGZAG[i]]);
        }
        baos.write(1);
        for (int i = 0; i < 64; i++) {
            baos.write(chrominanceQuant[ZIGZAG[i]]);
        }
        write2Bytes(ByteOrder.BIG_ENDIAN, baos.size() + 2, os);
        baos.writeTo(os);
    }

    private void writeSOF0(final OutputStream os, final int width, final int height) throws IOException {
        os.write(0xff);
        os.write(0xc0);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(8);
        write2Bytes(ByteOrder.BIG_ENDIAN, height, baos);
        write2Bytes(ByteOrder.BIG_ENDIAN, width, baos);
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
        write2Bytes(ByteOrder.BIG_ENDIAN, baos.size() + 2, os);
        baos.writeTo(os);
    }

    private void writeDHT(final OutputStream os) throws IOException {
        os.write(0xff);
        os.write(0xc4);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeHuffmanTable(baos, 0, 0, STD_DC_LUMINANCE_LENGTHS, STD_DC_LUMINANCE_VALUES);
        writeHuffmanTable(baos, 1, 0, STD_AC_LUMINANCE_LENGTHS, STD_AC_LUMINANCE_VALUES);
        writeHuffmanTable(baos, 0, 1, STD_DC_CHROMINANCE_LENGTHS, STD_DC_CHROMINANCE_VALUES);
        writeHuffmanTable(baos, 1, 1, STD_AC_CHROMINANCE_LENGTHS, STD_AC_CHROMINANCE_VALUES);
        write2Bytes(ByteOrder.BIG_ENDIAN, baos.size() + 2, os);
        baos.writeTo(os);
    }

    private void writeHuffmanTable(final OutputStream os, final int tableClass, final int destinationId,
                                    final int[] lengths, final int[] values) throws IOException {
        final int tcTh = (tableClass << 4) | (destinationId & 0xf);
        os.write(tcTh);
        for (int i = 0; i < 16; i++) {
            os.write(lengths[i]);
        }
        for (int value : values) {
            os.write(value);
        }
    }

    private void writeSOS(final OutputStream os) throws IOException {
        os.write(0xff);
        os.write(0xda);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(3);
        baos.write(1);
        baos.write(0);
        baos.write(2);
        baos.write(0x11);
        baos.write(3);
        baos.write(0x11);
        baos.write(0);
        baos.write(0x3f);
        baos.write(0);
        write2Bytes(ByteOrder.BIG_ENDIAN, baos.size() + 2, os);
        baos.writeTo(os);
    }

    private void encodeImageData(final float[][][] dctBlocks, final BitOutputStream bos,
                                  final int width, final int height) throws IOException {
        final HuffmanEncoder dcLumEnc = new HuffmanEncoder(STD_DC_LUMINANCE_LENGTHS, STD_DC_LUMINANCE_VALUES);
        final HuffmanEncoder acLumEnc = new HuffmanEncoder(STD_AC_LUMINANCE_LENGTHS, STD_AC_LUMINANCE_VALUES);
        final HuffmanEncoder dcChromEnc = new HuffmanEncoder(STD_DC_CHROMINANCE_LENGTHS, STD_DC_CHROMINANCE_VALUES);
        final HuffmanEncoder acChromEnc = new HuffmanEncoder(STD_AC_CHROMINANCE_LENGTHS, STD_AC_CHROMINANCE_VALUES);

        final int mcuWidth = (width + 7) / 8;
        final int mcuHeight = (height + 7) / 8;

        int prevDC0 = 0;
        int prevDC1 = 0;
        int prevDC2 = 0;

        for (int mcuY = 0; mcuY < mcuHeight; mcuY++) {
            for (int mcuX = 0; mcuX < mcuWidth; mcuX++) {
                final int mcuIndex = mcuY * mcuWidth + mcuX;

                for (int c = 0; c < 3; c++) {
                    final HuffmanEncoder dcEnc = c == 0 ? dcLumEnc : dcChromEnc;
                    final HuffmanEncoder acEnc = c == 0 ? acLumEnc : acChromEnc;
                    int prevDC = c == 0 ? prevDC0 : (c == 1 ? prevDC1 : prevDC2);

                    final float[] block = dctBlocks[c][mcuIndex];
                    final int dc = (int) block[0];
                    final int diff = dc - prevDC;

                    encodeValue(dcEnc, diff, bos);

                    int zeroRun = 0;
                    for (int i = 1; i < 64; i++) {
                        final int ac = (int) block[i];
                        if (ac == 0) {
                            zeroRun++;
                        } else {
                            while (zeroRun >= 16) {
                                encodeValue(acEnc, 0xf0, bos);
                                zeroRun -= 16;
                            }
                            final int code = (zeroRun << 4) | getCategory(ac);
                            encodeValue(acEnc, code, bos);
                            encodeBits(ac, getCategory(ac), bos);
                            zeroRun = 0;
                        }
                    }
                    if (zeroRun > 0) {
                        encodeValue(acEnc, 0x00, bos);
                    }

                    if (c == 0) {
                        prevDC0 = dc;
                    } else if (c == 1) {
                        prevDC1 = dc;
                    } else {
                        prevDC2 = dc;
                    }
                }
            }
        }
    }

    private void encodeValue(final HuffmanEncoder enc, final int value, final BitOutputStream bos) throws IOException {
        bos.writeBits(enc.getCode(value), enc.getLength(value));
    }

    private void encodeBits(final int value, final int numBits, final BitOutputStream bos) throws IOException {
        int bits = value;
        if (bits < 0) {
            bits = bits + ((1 << numBits) - 1);
        }
        bos.writeBits(bits, numBits);
    }

    private int getCategory(final int value) {
        int v = value < 0 ? -value : value;
        int category = 0;
        while (v != 0) {
            category++;
            v >>= 1;
        }
        return category;
    }
}
