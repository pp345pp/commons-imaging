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

import static org.apache.commons.imaging.common.BinaryFunctions.read4Bytes;
import static org.apache.commons.imaging.common.BinaryFunctions.readBytes;
import static org.apache.commons.imaging.common.BinaryFunctions.skipBytes;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteOrder;
import java.util.ArrayList;

import org.apache.commons.imaging.AbstractImageParser;
import org.apache.commons.imaging.ImageFormat;
import org.apache.commons.imaging.ImageFormats;
import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.apache.commons.imaging.common.ImageBuilder;
import org.apache.commons.imaging.common.XmpEmbeddable;
import org.apache.commons.imaging.common.XmpImagingParameters;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.TiffImageParser;
import org.apache.commons.imaging.formats.webp.chunks.AbstractWebPChunk;
import org.apache.commons.imaging.formats.webp.chunks.WebPChunkVp8;
import org.apache.commons.imaging.formats.webp.chunks.WebPChunkVp8l;
import org.apache.commons.imaging.formats.webp.chunks.WebPChunkVp8x;
import org.apache.commons.imaging.formats.webp.chunks.WebPChunkXml;
import org.apache.commons.imaging.internal.SafeOperations;

/**
 * WebP image parser.
 *
 * @since 1.0.0-alpha4
 */
public class WebPImageParser extends AbstractImageParser<WebPImagingParameters> implements XmpEmbeddable<WebPImagingParameters> {

    private static final class ChunksReader implements Closeable {
        private final InputStream is;
        private final WebPChunkType[] chunkTypes;
        private int sizeCount = 4;
        private boolean firstChunk = true;

        final int fileSize;

        ChunksReader(final ByteSource byteSource) throws IOException, ImagingException {
            this(byteSource, (WebPChunkType[]) null);
        }

        ChunksReader(final ByteSource byteSource, final WebPChunkType... chunkTypes) throws ImagingException, IOException {
            this.is = byteSource.getInputStream();
            this.chunkTypes = chunkTypes;
            this.fileSize = readFileHeader(is);
        }

        @Override
        public void close() throws IOException {
            is.close();
        }

        int getOffset() {
            return SafeOperations.add(sizeCount, 8); // File Header
        }

        AbstractWebPChunk readChunk() throws ImagingException, IOException {
            while (sizeCount < fileSize) {
                final int type = read4Bytes("Chunk Type", is, "Not a valid WebP file", ByteOrder.LITTLE_ENDIAN);
                final int payloadSize = read4Bytes("Chunk Size", is, "Not a valid WebP file", ByteOrder.LITTLE_ENDIAN);
                if (payloadSize < 0) {
                    throw new ImagingException("Chunk Payload is too long:" + payloadSize);
                }
                final boolean padding = payloadSize % 2 != 0;
                final int chunkSize = SafeOperations.add(8, padding ? 1 : 0, payloadSize);

                if (firstChunk) {
                    firstChunk = false;
                    if (type != WebPChunkType.VP8.value && type != WebPChunkType.VP8L.value && type != WebPChunkType.VP8X.value) {
                        throw new ImagingException("First Chunk must be VP8, VP8L or VP8X");
                    }
                }

                if (chunkTypes != null) {
                    boolean skip = true;
                    for (final WebPChunkType t : chunkTypes) {
                        if (t.value == type) {
                            skip = false;
                            break;
                        }
                    }
                    if (skip) {
                        skipBytes(is, payloadSize + (padding ? 1 : 0));
                        sizeCount = SafeOperations.add(sizeCount, chunkSize);
                        continue;
                    }
                }

                final byte[] bytes = readBytes("Chunk Payload", is, payloadSize);
                final AbstractWebPChunk chunk = WebPChunkType.makeChunk(type, payloadSize, bytes);
                if (padding) {
                    skipBytes(is, 1);
                }

                sizeCount = SafeOperations.add(sizeCount, chunkSize);
                return chunk; // NOPMD How can we do this better?
            }

            if (firstChunk) {
                throw new ImagingException("No WebP chunks found");
            }
            return null;
        }
    }

    private static final String DEFAULT_EXTENSION = ImageFormats.WEBP.getDefaultExtension();

    private static final String[] ACCEPTED_EXTENSIONS = ImageFormats.WEBP.getExtensions();

    /**
     * Reads the file header of WebP file.
     *
     * @return file size in file header (including the WebP signature, excluding the TIFF signature and the file size field).
     */
    private static int readFileHeader(final InputStream is) throws IOException, ImagingException {
        final byte[] buffer = new byte[4];
        if (is.read(buffer) < 4 || !WebPConstants.RIFF_SIGNATURE.equals(buffer)) {
            throw new ImagingException("Not a valid WebP file");
        }

        final int fileSize = read4Bytes("File Size", is, "Not a valid WebP file", ByteOrder.LITTLE_ENDIAN);
        if (fileSize < 0) {
            throw new ImagingException("File size is too long:" + fileSize);
        }

        if (is.read(buffer) < 4 || !WebPConstants.WEBP_SIGNATURE.equals(buffer)) {
            throw new ImagingException("Not a valid WebP file");
        }

        return fileSize;
    }

    /**
     * Constructs a new instance with the big-endian byte order.
     */
    public WebPImageParser() {
        // empty
    }

    @Override
    public boolean dumpImageFile(final PrintWriter pw, final ByteSource byteSource) throws ImagingException, IOException {
        pw.println("webp.dumpImageFile");
        try (ChunksReader reader = new ChunksReader(byteSource)) {
            int offset = reader.getOffset();
            AbstractWebPChunk chunk = reader.readChunk();
            if (chunk == null) {
                throw new ImagingException("No WebP chunks found");
            }

            // TODO: this does not look too risky; a user could craft an image
            // with millions of chunks, that are really expensive to dump,
            // but that should result in a large image, where we can short-
            // -circuit the operation somewhere else - if needed.
            do {
                chunk.dump(pw, offset);

                offset = reader.getOffset();
                chunk = reader.readChunk();
            } while (chunk != null);
        }
        return true;
    }

    @Override
    protected String[] getAcceptedExtensions() {
        return ACCEPTED_EXTENSIONS;
    }

    @Override
    protected ImageFormat[] getAcceptedTypes() {
        return new ImageFormat[] { ImageFormats.WEBP };
    }

    @Override
    public BufferedImage getBufferedImage(final ByteSource byteSource, final WebPImagingParameters params) throws ImagingException, IOException {
        try (ChunksReader reader = new ChunksReader(byteSource)) {
            final AbstractWebPChunk chunk = reader.readChunk();
            if (chunk instanceof WebPChunkVp8l) {
                return decodeVP8L((WebPChunkVp8l) chunk);
            }
            if (chunk instanceof WebPChunkVp8x) {
                final WebPChunkVp8x vp8x = (WebPChunkVp8x) chunk;
                AbstractWebPChunk innerChunk = reader.readChunk();
                while (innerChunk != null) {
                    if (innerChunk.getType() == WebPChunkType.VP8L.value) {
                        return decodeVP8L((WebPChunkVp8l) innerChunk);
                    }
                    innerChunk = reader.readChunk();
                }
                throw new ImagingException("No VP8L chunk found in extended WebP");
            }
            throw new ImagingException("Reading WebP files is currently not supported for this format");
        }
    }

    @Override
    public String getDefaultExtension() {
        return DEFAULT_EXTENSION;
    }

    @Override
    public WebPImagingParameters getDefaultParameters() {
        return new WebPImagingParameters();
    }

    @Override
    public byte[] getIccProfileBytes(final ByteSource byteSource, final WebPImagingParameters params) throws ImagingException, IOException {
        try (ChunksReader reader = new ChunksReader(byteSource, WebPChunkType.ICCP)) {
            final AbstractWebPChunk chunk = reader.readChunk();
            return chunk == null ? null : chunk.getBytes();
        }
    }

    @Override
    public ImageInfo getImageInfo(final ByteSource byteSource, final WebPImagingParameters params) throws ImagingException, IOException {
        try (ChunksReader reader = new ChunksReader(byteSource, WebPChunkType.VP8, WebPChunkType.VP8L, WebPChunkType.VP8X, WebPChunkType.ANMF)) {
            final String formatDetails;
            final int width;
            final int height;
            int numberOfImages;
            boolean hasAlpha = false;
            ImageInfo.ColorType colorType = ImageInfo.ColorType.RGB;

            AbstractWebPChunk chunk = reader.readChunk();
            if (chunk instanceof WebPChunkVp8) {
                formatDetails = "WebP/Lossy";
                numberOfImages = 1;

                final WebPChunkVp8 vp8 = (WebPChunkVp8) chunk;
                width = vp8.getWidth();
                height = vp8.getHeight();
                colorType = ImageInfo.ColorType.YCbCr;
            } else if (chunk instanceof WebPChunkVp8l) {
                formatDetails = "WebP/Lossless";
                numberOfImages = 1;

                final WebPChunkVp8l vp8l = (WebPChunkVp8l) chunk;
                width = vp8l.getImageWidth();
                height = vp8l.getImageHeight();
            } else if (chunk instanceof WebPChunkVp8x) {
                final WebPChunkVp8x vp8x = (WebPChunkVp8x) chunk;
                width = vp8x.getCanvasWidth();
                height = vp8x.getCanvasHeight();
                hasAlpha = ((WebPChunkVp8x) chunk).hasAlpha();

                if (vp8x.hasAnimation()) {
                    formatDetails = "WebP/Animation";

                    numberOfImages = 0;
                    while ((chunk = reader.readChunk()) != null) {
                        if (chunk.getType() == WebPChunkType.ANMF.value) {
                            numberOfImages++;
                        }
                    }

                } else {
                    numberOfImages = 1;
                    chunk = reader.readChunk();

                    if (chunk == null) {
                        throw new ImagingException("Image has no content");
                    }

                    if (chunk.getType() == WebPChunkType.ANMF.value) {
                        throw new ImagingException("Non animated image should not contain ANMF chunks");
                    }

                    if (chunk.getType() == WebPChunkType.VP8.value) {
                        formatDetails = "WebP/Lossy (Extended)";
                        colorType = ImageInfo.ColorType.YCbCr;
                    } else if (chunk.getType() == WebPChunkType.VP8L.value) {
                        formatDetails = "WebP/Lossless (Extended)";
                    } else {
                        throw new ImagingException("Unknown WebP chunk type: " + chunk);
                    }
                }
            } else {
                throw new ImagingException("Unknown WebP chunk type: " + chunk);
            }

            return new ImageInfo(formatDetails, 32, new ArrayList<>(), ImageFormats.WEBP, "webp", height, "image/webp", numberOfImages, -1, -1, -1, -1, width,
                    false, hasAlpha, false, colorType, ImageInfo.CompressionAlgorithm.UNKNOWN);
        }
    }

    @Override
    public Dimension getImageSize(final ByteSource byteSource, final WebPImagingParameters params) throws ImagingException, IOException {
        try (ChunksReader reader = new ChunksReader(byteSource)) {
            final AbstractWebPChunk chunk = reader.readChunk();
            if (chunk instanceof WebPChunkVp8) {
                final WebPChunkVp8 vp8 = (WebPChunkVp8) chunk;
                return new Dimension(vp8.getWidth(), vp8.getHeight());
            }
            if (chunk instanceof WebPChunkVp8l) {
                final WebPChunkVp8l vp8l = (WebPChunkVp8l) chunk;
                return new Dimension(vp8l.getImageWidth(), vp8l.getImageHeight());
            }
            if (chunk instanceof WebPChunkVp8x) {
                final WebPChunkVp8x vp8x = (WebPChunkVp8x) chunk;
                return new Dimension(vp8x.getCanvasWidth(), vp8x.getCanvasHeight());
            }
            throw new ImagingException("Unknown WebP chunk type: " + chunk);
        }
    }

    @Override
    public WebPImageMetadata getMetadata(final ByteSource byteSource, final WebPImagingParameters params) throws ImagingException, IOException {
        try (ChunksReader reader = new ChunksReader(byteSource, WebPChunkType.EXIF)) {
            final AbstractWebPChunk chunk = reader.readChunk();
            return chunk == null ? null : new WebPImageMetadata((TiffImageMetadata) new TiffImageParser().getMetadata(chunk.getBytes()));
        }
    }

    @Override
    public String getName() {
        return "WebP-Custom";
    }

    @Override
    public String getXmpXml(final ByteSource byteSource, final XmpImagingParameters<WebPImagingParameters> params) throws ImagingException, IOException {
        try (ChunksReader reader = new ChunksReader(byteSource, WebPChunkType.XMP)) {
            final WebPChunkXml chunk = (WebPChunkXml) reader.readChunk();
            return chunk == null ? null : chunk.getXml();
        }
    }

    @Override
    public void writeImage(final BufferedImage src, final OutputStream os, final WebPImagingParameters params)
            throws ImagingException, IOException {
        new WebPImageWriter().writeImage(src, os, params);
    }

    private static BufferedImage decodeVP8L(final WebPChunkVp8l chunk) throws ImagingException {
        final int width = chunk.getImageWidth();
        final int height = chunk.getImageHeight();
        final boolean hasAlpha = chunk.hasAlpha();
        final byte[] data = chunk.getBytes();
        final Vp8lBitReader reader = new Vp8lBitReader(data, 5);

        final int isFirstPart = reader.readBits(1);
        if (isFirstPart != 1) {
            throw new ImagingException("Expected first part of VP8L image");
        }

        final int hasTransform = reader.readBits(1);
        if (hasTransform == 1) {
            throw new ImagingException("VP8L transforms are not supported");
        }

        final int colorCacheBits = reader.readBits(4);
        if (colorCacheBits != 0) {
            throw new ImagingException("VP8L color cache is not supported");
        }

        final int numHuffmanCodes = reader.readBits(2) + 1;
        final int[][] huffmanCodeLengths = new int[numHuffmanCodes][];
        final int[][] huffmanCodes = new int[numHuffmanCodes][];
        for (int i = 0; i < numHuffmanCodes; i++) {
            final int[] lengths = readHuffmanCodeLengths(reader);
            huffmanCodeLengths[i] = lengths;
            huffmanCodes[i] = buildCanonicalCodes(lengths);
        }

        final ImageBuilder imageBuilder = new ImageBuilder(width, height, hasAlpha);
        final int pixelCount = width * height;

        for (int i = 0; i < pixelCount; i++) {
            int a = 0xff;
            if (hasAlpha && numHuffmanCodes >= 4) {
                a = readHuffmanSymbol(reader, huffmanCodes[3], huffmanCodeLengths[3]);
            }
            final int g = readHuffmanSymbol(reader, huffmanCodes[0], huffmanCodeLengths[0]);
            final int r = readHuffmanSymbol(reader, huffmanCodes[1], huffmanCodeLengths[1]);
            final int b = readHuffmanSymbol(reader, huffmanCodes[2], huffmanCodeLengths[2]);

            final int argb = (a << 24) | (r << 16) | (g << 8) | b;
            final int x = i % width;
            final int y = i / width;
            imageBuilder.setRgb(x, y, argb);
        }

        return imageBuilder.getBufferedImage();
    }

    private static int[] readHuffmanCodeLengths(final Vp8lBitReader reader) throws ImagingException {
        final int isSimple = reader.readBits(1);
        if (isSimple == 1) {
            throw new ImagingException("Simple Huffman codes are not supported");
        }

        final int numCodeLengthCodes = reader.readBits(4) + 4;
        final int[] metaCodeLengths = new int[19];
        for (int i = 0; i < numCodeLengthCodes; i++) {
            metaCodeLengths[CODE_LENGTH_ORDER[i]] = reader.readBits(3);
        }

        final int[] metaCodes = buildCanonicalCodes(metaCodeLengths);

        final int[] codeLengths = new int[256];
        int symbol = 0;
        while (symbol < 256) {
            final int code = readHuffmanSymbol(reader, metaCodes, metaCodeLengths);
            if (code < 16) {
                codeLengths[symbol++] = code;
            } else if (code == 16) {
                final int extra = reader.readBits(2);
                final int repeat = 3 + extra;
                final int prev = symbol > 0 ? codeLengths[symbol - 1] : 0;
                for (int j = 0; j < repeat && symbol < 256; j++) {
                    codeLengths[symbol++] = prev;
                }
            } else if (code == 17) {
                final int extra = reader.readBits(3);
                final int repeat = 3 + extra;
                for (int j = 0; j < repeat && symbol < 256; j++) {
                    codeLengths[symbol++] = 0;
                }
            } else if (code == 18) {
                final int extra = reader.readBits(7);
                final int repeat = 11 + extra;
                for (int j = 0; j < repeat && symbol < 256; j++) {
                    codeLengths[symbol++] = 0;
                }
            }
        }
        return codeLengths;
    }

    private static int[] buildCanonicalCodes(final int[] codeLengths) {
        final int maxLength = 15;
        final int[] bitCount = new int[maxLength + 1];
        for (final int len : codeLengths) {
            if (len > 0) {
                bitCount[len]++;
            }
        }
        int code = 0;
        final int[] nextCode = new int[maxLength + 1];
        for (int bits = 1; bits <= maxLength; bits++) {
            code = (code + bitCount[bits - 1]) << 1;
            nextCode[bits] = code;
        }
        final int[] codes = new int[codeLengths.length];
        for (int i = 0; i < codes.length; i++) {
            final int len = codeLengths[i];
            if (len > 0) {
                codes[i] = nextCode[len]++;
            }
        }
        return codes;
    }

    private static int readHuffmanSymbol(final Vp8lBitReader reader, final int[] codes, final int[] codeLengths) throws ImagingException {
        int code = 0;
        for (int len = 1; len <= 15; len++) {
            code = (code << 1) | reader.readBits(1);
            for (int i = 0; i < codeLengths.length; i++) {
                if (codeLengths[i] == len && codes[i] == code) {
                    return i;
                }
            }
        }
        throw new ImagingException("Invalid Huffman code in VP8L bitstream");
    }

    private static final int[] CODE_LENGTH_ORDER = { 17, 18, 0, 1, 2, 3, 4, 5, 16, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 };

    private static final class Vp8lBitReader {
        private final byte[] data;
        private int bytePos;
        private int bitPos;

        Vp8lBitReader(final byte[] data, final int offset) {
            this.data = data;
            this.bytePos = offset;
            this.bitPos = 0;
        }

        int readBits(final int numBits) {
            int result = 0;
            int remaining = numBits;
            int shift = 0;
            while (remaining > 0) {
                if (bytePos >= data.length) {
                    return result;
                }
                final int bitsAvailable = 8 - bitPos;
                final int bitsToRead = Math.min(remaining, bitsAvailable);
                final int mask = (1 << bitsToRead) - 1;
                final int value = (data[bytePos] >> bitPos) & mask;
                result |= value << shift;
                shift += bitsToRead;
                bitPos += bitsToRead;
                remaining -= bitsToRead;
                if (bitPos == 8) {
                    bitPos = 0;
                    bytePos++;
                }
            }
            return result;
        }
    }
}
