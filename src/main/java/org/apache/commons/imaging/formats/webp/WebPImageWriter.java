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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.util.Arrays;

import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.common.AbstractBinaryOutputStream;

/**
 * WebP image writer with VP8L lossless encoding support.
 *
 * @since 1.0.0-alpha7
 */
public class WebPImageWriter {

    private static final int[] CODE_LENGTH_ORDER = { 17, 18, 0, 1, 2, 3, 4, 5, 16, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 };

    private static final int HUFFMAN_CODE_GREEN = 0;
    private static final int HUFFMAN_CODE_RED = 1;
    private static final int HUFFMAN_CODE_BLUE = 2;
    private static final int HUFFMAN_CODE_ALPHA = 3;

    private static final int ALPHABET_SIZE = 256;

    private static final class BitWriter {
        private final byte[] buffer;
        private int bitPos;
        private int bytePos;

        BitWriter(final int capacity) {
            this.buffer = new byte[capacity];
            this.bitPos = 0;
            this.bytePos = 0;
        }

        void writeBits(final int value, final int numBits) {
            int remaining = numBits;
            int v = value;
            while (remaining > 0) {
                final int bitsInCurrentByte = 8 - bitPos;
                final int bitsToWrite = Math.min(remaining, bitsInCurrentByte);
                final int mask = (1 << bitsToWrite) - 1;
                buffer[bytePos] |= (byte) ((v & mask) << bitPos);
                v >>>= bitsToWrite;
                bitPos += bitsToWrite;
                remaining -= bitsToWrite;
                if (bitPos == 8) {
                    bitPos = 0;
                    bytePos++;
                }
            }
        }

        void writeBit(final int bit) {
            writeBits(bit, 1);
        }

        byte[] toByteArray() {
            final int length = bitPos > 0 ? bytePos + 1 : bytePos;
            return Arrays.copyOf(buffer, length);
        }

        int getBytePos() {
            return bytePos;
        }
    }

    private static final class HuffmanTree {
        final int[] codes;
        final int[] codeLengths;
        final int maxCodeLength;

        HuffmanTree(final int[] codeLengths, final int[] codes, final int maxCodeLength) {
            this.codeLengths = codeLengths;
            this.codes = codes;
            this.maxCodeLength = maxCodeLength;
        }
    }

    /**
     * Writes a BufferedImage as a WebP (VP8L lossless) to the output stream.
     *
     * @param src    the source image
     * @param os     the output stream
     * @param params the WebP imaging parameters
     * @throws ImagingException if the image format is invalid
     * @throws IOException      if an I/O error occurs
     */
    public void writeImage(final BufferedImage src, final OutputStream os, final WebPImagingParameters params)
            throws ImagingException, IOException {
        final boolean hasAlpha = hasRealAlpha(src);
        final byte[] exifData = params != null ? params.getExifData() : null;
        final String xmpXml = params != null ? params.getXmpXml() : null;
        final int compressionLevel = params != null ? params.getCompressionLevel() : 4;

        final boolean hasExif = exifData != null && exifData.length > 0;
        final boolean hasXmp = xmpXml != null && !xmpXml.isEmpty();

        final byte[] vp8lData = encodeVP8L(src, hasAlpha, compressionLevel);

        if (hasExif || hasXmp) {
            writeExtendedFormat(os, src.getWidth(), src.getHeight(), hasAlpha, hasExif, hasXmp, vp8lData, exifData, xmpXml);
        } else {
            writeSimpleFormat(os, vp8lData);
        }
    }

    private static boolean hasRealAlpha(final BufferedImage src) {
        if (src.getColorModel().hasAlpha()) {
            final int width = src.getWidth();
            final int height = src.getHeight();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    final int alpha = src.getRGB(x, y) >>> 24;
                    if (alpha != 0xff) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static byte[] encodeVP8L(final BufferedImage src, final boolean hasAlpha, final int compressionLevel)
            throws ImagingException {
        final int width = src.getWidth();
        final int height = src.getHeight();
        final int pixelCount = width * height;
        final int numChannels = hasAlpha ? 4 : 3;

        final int[] greenFreq = new int[ALPHABET_SIZE];
        final int[] redFreq = new int[ALPHABET_SIZE];
        final int[] blueFreq = new int[ALPHABET_SIZE];
        final int[] alphaFreq = hasAlpha ? new int[ALPHABET_SIZE] : null;

        final byte[] greenPixels = new byte[pixelCount];
        final byte[] redPixels = new byte[pixelCount];
        final byte[] bluePixels = new byte[pixelCount];
        final byte[] alphaPixels = hasAlpha ? new byte[pixelCount] : null;

        int idx = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int argb = src.getRGB(x, y);
                final int a = (argb >> 24) & 0xff;
                final int r = (argb >> 16) & 0xff;
                final int g = (argb >> 8) & 0xff;
                final int b = argb & 0xff;

                greenPixels[idx] = (byte) g;
                redPixels[idx] = (byte) r;
                bluePixels[idx] = (byte) b;
                greenFreq[g]++;
                redFreq[r]++;
                blueFreq[b]++;

                if (hasAlpha) {
                    alphaPixels[idx] = (byte) a;
                    alphaFreq[a]++;
                }
                idx++;
            }
        }

        final HuffmanTree greenTree = buildHuffmanTree(greenFreq, compressionLevel);
        final HuffmanTree redTree = buildHuffmanTree(redFreq, compressionLevel);
        final HuffmanTree blueTree = buildHuffmanTree(blueFreq, compressionLevel);
        final HuffmanTree alphaTree = hasAlpha ? buildHuffmanTree(alphaFreq, compressionLevel) : null;

        final int numHuffmanCodes = hasAlpha ? 4 : 3;
        final int estimatedMaxBits = 40 + numHuffmanCodes * (19 * 3 + ALPHABET_SIZE * 15 + 256 * 4)
                + pixelCount * numChannels * 16;
        final BitWriter bw = new BitWriter(estimatedMaxBits / 8 + 1);

        bw.writeBit(1);

        bw.writeBit(0);

        bw.writeBits(0, 4);

        bw.writeBits(numHuffmanCodes - 1, 2);

        writeHuffmanCode(bw, greenTree, ALPHABET_SIZE);
        writeHuffmanCode(bw, redTree, ALPHABET_SIZE);
        writeHuffmanCode(bw, blueTree, ALPHABET_SIZE);
        if (hasAlpha) {
            writeHuffmanCode(bw, alphaTree, ALPHABET_SIZE);
        }

        for (int i = 0; i < pixelCount; i++) {
            if (hasAlpha) {
                writeSymbol(bw, alphaTree, alphaPixels[i] & 0xff);
            }
            writeSymbol(bw, greenTree, greenPixels[i] & 0xff);
            writeSymbol(bw, redTree, redPixels[i] & 0xff);
            writeSymbol(bw, blueTree, bluePixels[i] & 0xff);
        }

        final byte[] bitstreamData = bw.toByteArray();
        final byte[] header = new byte[5];
        header[0] = 0x2f;
        header[1] = (byte) ((width - 1) & 0xff);
        header[2] = (byte) (((width - 1) >> 8) & 0x3f);
        header[2] |= (byte) (((height - 1) & 0x3f) << 6);
        header[3] = (byte) (((height - 1) >> 6) & 0xff);
        header[4] = (byte) (((height - 1) >> 14) & 0x0f);
        if (hasAlpha) {
            header[4] |= 0x10;
        }

        final byte[] result = new byte[header.length + bitstreamData.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(bitstreamData, 0, result, header.length, bitstreamData.length);

        return result;
    }

    private static HuffmanTree buildHuffmanTree(final int[] freq, final int compressionLevel) {
        final int maxCodeLength = Math.min(8 + compressionLevel / 2, 15);

        final int[] sortedSymbols = new int[ALPHABET_SIZE];
        for (int i = 0; i < ALPHABET_SIZE; i++) {
            sortedSymbols[i] = i;
        }

        for (int i = 0; i < ALPHABET_SIZE - 1; i++) {
            for (int j = i + 1; j < ALPHABET_SIZE; j++) {
                if (freq[sortedSymbols[i]] < freq[sortedSymbols[j]]) {
                    final int tmp = sortedSymbols[i];
                    sortedSymbols[i] = sortedSymbols[j];
                    sortedSymbols[j] = tmp;
                }
            }
        }

        final int[] codeLengths = new int[ALPHABET_SIZE];
        final int[] bitLengthCounts = new int[maxCodeLength + 1];

        for (int i = 0; i < ALPHABET_SIZE; i++) {
            final int codeLen;
            if (compressionLevel <= 1) {
                codeLen = maxCodeLength;
            } else {
                final int rank = i;
                if (rank < 2) {
                    codeLen = 1 + compressionLevel / 9;
                } else if (rank < 8) {
                    codeLen = 2 + compressionLevel / 5;
                } else if (rank < 32) {
                    codeLen = 4 + compressionLevel / 4;
                } else if (rank < 128) {
                    codeLen = Math.min(6 + compressionLevel / 3, maxCodeLength);
                } else {
                    codeLen = Math.min(8 + compressionLevel / 3, maxCodeLength);
                }
            }
            final int symbol = sortedSymbols[i];
            codeLengths[symbol] = codeLen;
            bitLengthCounts[codeLen]++;
        }

        int code = 0;
        final int[] nextCode = new int[maxCodeLength + 1];
        for (int bits = 1; bits <= maxCodeLength; bits++) {
            code = (code + bitLengthCounts[bits - 1]) << 1;
            nextCode[bits] = code;
        }

        final int[] codes = new int[ALPHABET_SIZE];
        for (int i = 0; i < ALPHABET_SIZE; i++) {
            final int len = codeLengths[i];
            if (len > 0) {
                codes[i] = nextCode[len]++;
            }
        }

        return new HuffmanTree(codeLengths, codes, maxCodeLength);
    }

    private static void writeHuffmanCode(final BitWriter bw, final HuffmanTree tree, final int alphabetSize) {
        bw.writeBit(0);

        final int[] metaCodeLengths = new int[19];
        final boolean[] usedSymbols = new boolean[19];
        final int[] codeLengthCodes = encodeCodeLengths(tree.codeLengths, alphabetSize);
        for (final int code : codeLengthCodes) {
            usedSymbols[code] = true;
        }
        usedSymbols[16] = true;
        usedSymbols[17] = true;
        usedSymbols[18] = true;
        usedSymbols[0] = true;

        int firstNonZero = 19;
        int lastNonZero = -1;
        for (int i = 0; i < 19; i++) {
            final int sym = CODE_LENGTH_ORDER[i];
            if (usedSymbols[sym]) {
                metaCodeLengths[sym] = 5;
                if (i < firstNonZero) {
                    firstNonZero = i;
                }
                lastNonZero = i;
            } else {
                metaCodeLengths[sym] = 0;
            }
        }

        final int numMetaCodes = lastNonZero - firstNonZero + 1;
        bw.writeBits(numMetaCodes - 4, 4);
        for (int i = firstNonZero; i <= lastNonZero; i++) {
            final int sym = CODE_LENGTH_ORDER[i];
            bw.writeBits(metaCodeLengths[sym], 3);
        }

        final int[] metaCodes = new int[19];
        final int[] metaBitCounts = new int[8];
        for (int i = 0; i < 19; i++) {
            final int len = metaCodeLengths[i];
            if (len > 0) {
                metaBitCounts[len]++;
            }
        }
        int metaCode = 0;
        final int[] metaNextCode = new int[8];
        for (int bits = 1; bits <= 7; bits++) {
            metaCode = (metaCode + metaBitCounts[bits - 1]) << 1;
            metaNextCode[bits] = metaCode;
        }
        for (int i = 0; i < 19; i++) {
            final int len = metaCodeLengths[i];
            if (len > 0) {
                metaCodes[i] = metaNextCode[len]++;
            }
        }

        for (final int codeLengthCode : codeLengthCodes) {
            writeSymbolWithCodes(bw, metaCodes, metaCodeLengths, codeLengthCode);
        }
    }

    private static int[] encodeCodeLengths(final int[] codeLengths, final int alphabetSize) {
        final int[] result = new int[alphabetSize * 2];
        int pos = 0;
        int i = 0;
        while (i < alphabetSize) {
            final int currentLen = codeLengths[i];
            int runLength = 1;
            while (i + runLength < alphabetSize && codeLengths[i + runLength] == currentLen) {
                runLength++;
            }

            if (currentLen == 0) {
                while (runLength >= 11) {
                    final int len = Math.min(runLength, 138);
                    result[pos++] = 18;
                    i += len;
                    runLength -= len;
                }
                if (runLength >= 3) {
                    result[pos++] = 17;
                    i += runLength;
                    runLength = 0;
                }
                while (runLength > 0) {
                    result[pos++] = 0;
                    i++;
                    runLength--;
                }
            } else {
                result[pos++] = currentLen;
                i++;
                runLength--;
                while (runLength >= 3) {
                    final int repeat = Math.min(runLength, 6);
                    result[pos++] = 16;
                    i += repeat - 1;
                    runLength -= repeat - 1;
                }
                while (runLength > 0) {
                    result[pos++] = currentLen;
                    i++;
                    runLength--;
                }
            }
        }
        return Arrays.copyOf(result, pos);
    }

    private static void writeSymbol(final BitWriter bw, final HuffmanTree tree, final int symbol) {
        final int code = tree.codes[symbol];
        final int len = tree.codeLengths[symbol];
        bw.writeBits(code, len);
    }

    private static void writeSymbolWithCodes(final BitWriter bw, final int[] codes, final int[] codeLengths, final int symbol) {
        bw.writeBits(codes[symbol], codeLengths[symbol]);
    }

    private static void writeSimpleFormat(final OutputStream os, final byte[] vp8lData) throws IOException {
        final int riffSize = 4 + vp8lData.length;
        final int fileSize = 4 + 4 + riffSize;
        final boolean padding = vp8lData.length % 2 != 0;

        final ByteArrayOutputStream headerBos = new ByteArrayOutputStream();
        final AbstractBinaryOutputStream bos = AbstractBinaryOutputStream.create(headerBos, ByteOrder.LITTLE_ENDIAN);

        WebPConstants.RIFF_SIGNATURE.writeTo(bos);
        bos.write4Bytes(fileSize);
        WebPConstants.WEBP_SIGNATURE.writeTo(bos);
        bos.write4Bytes(WebPChunkType.VP8L.value);
        bos.write4Bytes(vp8lData.length);
        bos.flush();

        os.write(headerBos.toByteArray());
        os.write(vp8lData);
        if (padding) {
            os.write(0);
        }
    }

    private static void writeExtendedFormat(final OutputStream os, final int width, final int height, final boolean hasAlpha,
            final boolean hasExif, final boolean hasXmp, final byte[] vp8lData, final byte[] exifData, final String xmpXml)
            throws IOException {
        int flags = 0;
        if (hasAlpha) {
            flags |= 0x10;
        }
        if (hasExif) {
            flags |= 0x08;
        }
        if (hasXmp) {
            flags |= 0x04;
        }

        final ByteArrayOutputStream chunksBos = new ByteArrayOutputStream();
        final AbstractBinaryOutputStream chunksOut = AbstractBinaryOutputStream.create(chunksBos, ByteOrder.LITTLE_ENDIAN);

        writeChunk(chunksOut, WebPChunkType.VP8X, createVP8XChunkData(width, height, flags));
        if (hasExif) {
            writeChunk(chunksOut, WebPChunkType.EXIF, exifData);
        }
        if (hasXmp) {
            writeChunk(chunksOut, WebPChunkType.XMP, xmpXml.getBytes("UTF-8"));
        }
        writeChunk(chunksOut, WebPChunkType.VP8L, vp8lData);
        chunksOut.flush();

        final byte[] chunksData = chunksBos.toByteArray();
        final int fileSize = 4 + chunksData.length;

        final ByteArrayOutputStream headerBos = new ByteArrayOutputStream();
        final AbstractBinaryOutputStream headerOut = AbstractBinaryOutputStream.create(headerBos, ByteOrder.LITTLE_ENDIAN);
        WebPConstants.RIFF_SIGNATURE.writeTo(headerOut);
        headerOut.write4Bytes(fileSize);
        WebPConstants.WEBP_SIGNATURE.writeTo(headerOut);
        headerOut.flush();

        os.write(headerBos.toByteArray());
        os.write(chunksData);
    }

    private static byte[] createVP8XChunkData(final int width, final int height, final int flags) {
        final byte[] data = new byte[10];
        data[0] = (byte) flags;
        data[4] = (byte) ((width - 1) & 0xff);
        data[5] = (byte) (((width - 1) >> 8) & 0xff);
        data[6] = (byte) (((width - 1) >> 16) & 0xff);
        data[7] = (byte) ((height - 1) & 0xff);
        data[8] = (byte) (((height - 1) >> 8) & 0xff);
        data[9] = (byte) (((height - 1) >> 16) & 0xff);
        return data;
    }

    private static void writeChunk(final AbstractBinaryOutputStream bos, final WebPChunkType chunkType, final byte[] data)
            throws IOException {
        bos.write4Bytes(chunkType.value);
        bos.write4Bytes(data.length);
        bos.write(data);
        if (data.length % 2 != 0) {
            bos.write(0);
        }
    }
}