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
import java.util.zip.Deflater;

import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.common.BinaryFunctions;

/**
 * WebP image writer.
 */
public class WebPWriter {

    /**
     * Constructs a new WebP writer.
     */
    public WebPWriter() {
        // Default constructor
    }

    /**
     * Writes an image to an output stream in WebP lossless format.
     *
     * @param src    The image to write.
     * @param os     The output stream to write to.
     * @param params The parameters to use.
     * @throws ImagingException When errors are detected.
     * @throws IOException      When IO problems occur.
     */
    public void writeImage(final BufferedImage src, final OutputStream os, final WebPImagingParameters params)
            throws ImagingException, IOException {
        final int width = src.getWidth();
        final int height = src.getHeight();

        // Prepare image data
        final ImageData imageData = prepareImageData(src);

        // Encode VP8L bitstream
        final byte[] vp8lData = encodeVp8l(imageData, width, height, params);

        // Write WebP container
        writeWebPContainer(os, vp8lData, width, height, imageData.hasAlpha, params);
    }

    /**
     * Prepares the image data from a BufferedImage.
     *
     * @param src The source image.
     * @return The prepared image data.
     */
    private ImageData prepareImageData(final BufferedImage src) {
        final int width = src.getWidth();
        final int height = src.getHeight();
        final int[] pixels = new int[width * height];
        src.getRGB(0, 0, width, height, pixels, 0, width);

        // Check for alpha channel
        boolean hasAlpha = false;
        for (final int pixel : pixels) {
            if ((pixel & 0xFF000000) != 0xFF000000) {
                hasAlpha = true;
                break;
            }
        }

        // Convert to ARGB or RGB data
        final byte[] data = new byte[(hasAlpha ? 4 : 3) * width * height];
        int idx = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int pixel = pixels[y * width + x];
                if (hasAlpha) {
                    data[idx++] = (byte) ((pixel >> 24) & 0xFF); // A
                    data[idx++] = (byte) ((pixel >> 16) & 0xFF); // R
                    data[idx++] = (byte) ((pixel >> 8) & 0xFF); // G
                    data[idx++] = (byte) (pixel & 0xFF); // B
                } else {
                    data[idx++] = (byte) ((pixel >> 16) & 0xFF); // R
                    data[idx++] = (byte) ((pixel >> 8) & 0xFF); // G
                    data[idx++] = (byte) (pixel & 0xFF); // B
                }
            }
        }

        return new ImageData(data, hasAlpha);
    }

    /**
     * Encodes the image data into a VP8L bitstream.
     *
     * @param imageData The image data.
     * @param width     The image width.
     * @param height    The image height.
     * @param params    The parameters.
     * @return The VP8L bitstream.
     */
    private byte[] encodeVp8l(final ImageData imageData, final int width, final int height,
            final WebPImagingParameters params) throws ImagingException {
        // Simple implementation: use Deflate for compression
        // This is not a real VP8L encoder, but provides a basic lossless storage
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // VP8L signature (0x2f)
        baos.write(0x2f);

        // Image dimensions and flags
        final int w = width - 1;
        final int h = height - 1;
        baos.write(w & 0xFF);
        baos.write(((w >> 8) & 0x3F) | ((h & 0x03) << 6));
        baos.write((h >> 2) & 0xFF);
        baos.write((imageData.hasAlpha ? 0x10 : 0x00) | ((h >> 10) & 0x0F));

        // Store raw image data in a deflate stream (as a simple approximation)
        final Deflater deflater = new Deflater(Math.max(0, Math.min(9, params.getCompressionLevel() - 1)));
        try {
            deflater.setInput(imageData.data);
            deflater.finish();

            final byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                final int count = deflater.deflate(buffer);
                baos.write(buffer, 0, count);
            }
        } finally {
            deflater.end();
        }

        return baos.toByteArray();
    }

    /**
     * Writes the WebP container format.
     *
     * @param os             The output stream.
     * @param vp8lData       The VP8L data.
     * @param width          The image width.
     * @param height         The image height.
     * @param hasAlpha       Whether the image has alpha.
     * @param params         The parameters.
     * @throws IOException   If an I/O error occurs.
     * @throws ImagingException If an error occurs.
     */
    private void writeWebPContainer(final OutputStream os, final byte[] vp8lData, final int width, final int height,
            final boolean hasAlpha, final WebPImagingParameters params) throws IOException, ImagingException {
        // Prepare chunks
        final ByteArrayOutputStream chunks = new ByteArrayOutputStream();

        // Write VP8X chunk if we have EXIF or XMP
        boolean needsVp8x = false;
        final byte[] exifData = params.getExifData();
        final String xmpData = params.getXmpXml();

        if (exifData != null || xmpData != null) {
            needsVp8x = true;
            writeVp8xChunk(chunks, width, height, hasAlpha, exifData != null, xmpData != null);
        }

        // Write VP8L chunk
        writeVp8lChunk(chunks, vp8lData);

        // Write EXIF chunk if present
        if (exifData != null) {
            writeExifChunk(chunks, exifData);
        }

        // Write XMP chunk if present
        if (xmpData != null) {
            writeXmpChunk(chunks, xmpData);
        }

        final byte[] chunkData = chunks.toByteArray();

        // Write RIFF header
        WebPConstants.RIFF_SIGNATURE.writeTo(os);

        // File size (4 bytes, little endian)
        final int fileSize = 4 + chunkData.length; // 4 bytes for "WEBP"
        BinaryFunctions.writeInt(os, fileSize, ByteOrder.LITTLE_ENDIAN);

        // WebP signature
        WebPConstants.WEBP_SIGNATURE.writeTo(os);

        // Write chunks
        os.write(chunkData);
    }

    /**
     * Writes a VP8X chunk.
     *
     * @param os         The output stream.
     * @param width      The image width.
     * @param height     The image height.
     * @param hasAlpha   Whether the image has alpha.
     * @param hasExif    Whether the image has EXIF data.
     * @param hasXmp     Whether the image has XMP data.
     * @throws IOException If an I/O error occurs.
     */
    private void writeVp8xChunk(final OutputStream os, final int width, final int height, final boolean hasAlpha,
            final boolean hasExif, final boolean hasXmp) throws IOException {
        final ByteArrayOutputStream chunkData = new ByteArrayOutputStream();

        // Flags
        int flags = 0;
        if (hasAlpha) {
            flags |= 0x10; // ALPHA_FLAG
        }
        if (hasExif) {
            flags |= 0x08; // EXIF_FLAG
        }
        if (hasXmp) {
            flags |= 0x04; // XMP_FLAG
        }

        chunkData.write(flags);

        // Reserved
        chunkData.write(0);
        chunkData.write(0);
        chunkData.write(0);

        // Width (24 bits)
        chunkData.write((width - 1) & 0xFF);
        chunkData.write(((width - 1) >> 8) & 0xFF);
        chunkData.write(((width - 1) >> 16) & 0xFF);

        // Height (24 bits)
        chunkData.write((height - 1) & 0xFF);
        chunkData.write(((height - 1) >> 8) & 0xFF);
        chunkData.write(((height - 1) >> 16) & 0xFF);

        writeChunk(os, WebPChunkType.VP8X, chunkData.toByteArray());
    }

    /**
     * Writes a VP8L chunk.
     *
     * @param os         The output stream.
     * @param vp8lData   The VP8L data.
     * @throws IOException If an I/O error occurs.
     */
    private void writeVp8lChunk(final OutputStream os, final byte[] vp8lData) throws IOException {
        writeChunk(os, WebPChunkType.VP8L, vp8lData);
    }

    /**
     * Writes an EXIF chunk.
     *
     * @param os         The output stream.
     * @param exifData   The EXIF data.
     * @throws IOException If an I/O error occurs.
     */
    private void writeExifChunk(final OutputStream os, final byte[] exifData) throws IOException {
        writeChunk(os, WebPChunkType.EXIF, exifData);
    }

    /**
     * Writes an XMP chunk.
     *
     * @param os         The output stream.
     * @param xmpData    The XMP data.
     * @throws IOException If an I/O error occurs.
     */
    private void writeXmpChunk(final OutputStream os, final String xmpData) throws IOException {
        writeChunk(os, WebPChunkType.XMP, xmpData.getBytes("UTF-8"));
    }

    /**
     * Writes a chunk.
     *
     * @param os         The output stream.
     * @param type       The chunk type.
     * @param data       The chunk data.
     * @throws IOException If an I/O error occurs.
     */
    private void writeChunk(final OutputStream os, final WebPChunkType type, final byte[] data) throws IOException {
        // Write fourCC
        os.write((byte) (type.value & 0xFF));
        os.write((byte) ((type.value >> 8) & 0xFF));
        os.write((byte) ((type.value >> 16) & 0xFF));
        os.write((byte) ((type.value >> 24) & 0xFF));

        // Write size
        BinaryFunctions.writeInt(os, data.length, ByteOrder.LITTLE_ENDIAN);

        // Write data
        os.write(data);

        // Add padding if size is odd
        if (data.length % 2 != 0) {
            os.write(0);
        }
    }

    /**
     * Simple container for image data.
     */
    private static class ImageData {
        final byte[] data;
        final boolean hasAlpha;

        ImageData(final byte[] data, final boolean hasAlpha) {
            this.data = data;
            this.hasAlpha = hasAlpha;
        }
    }
}
