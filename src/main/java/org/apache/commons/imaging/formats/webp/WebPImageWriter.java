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
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.common.AbstractBinaryOutputStream;
import org.apache.commons.imaging.common.LittleEndianBinaryOutputStream;
import org.apache.commons.imaging.formats.tiff.TiffImageParser;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;

/**
 * WebP image writer implementing VP8L lossless encoding.
 *
 * @since 1.0.0-alpha4
 */
class WebPImageWriter {

    private static final int VP8L_SIGNATURE = 0x2F;
    private static final int VP8L_VERSION = 0;

    WebPImageWriter() {
    }

    void writeImage(final BufferedImage src, final OutputStream os, final WebPImagingParameters params) throws ImagingException, IOException {
        if (src == null) {
            throw new ImagingException("Source image is null");
        }
        if (os == null) {
            throw new ImagingException("Output stream is null");
        }

        final int width = src.getWidth();
        final int height = src.getHeight();

        if (width <= 0 || height <= 0 || width > 16383 || height > 16383) {
            throw new ImagingException("Image dimensions out of range: " + width + "x" + height);
        }

        final int compressionLevel = params != null ? params.getCompressionLevel() : WebPImagingParameters.DEFAULT_COMPRESSION_LEVEL;

        final boolean hasAlpha = hasAlphaChannel(src);
        final byte[] vp8lData = encodeVp8l(src, width, height, hasAlpha, compressionLevel);

        byte[] exifData = null;
        if (params != null) {
            if (params.getExifBytes() != null) {
                exifData = params.getExifBytes();
            } else if (params.getTiffOutputSet() != null) {
                exifData = buildExifData(params.getTiffOutputSet());
            }
        }
        final String xmpXml = params != null ? params.getXmpXml() : null;

        writeWebPFile(os, vp8lData, exifData, xmpXml);
    }

    private byte[] buildExifData(final TiffOutputSet outputSet) throws ImagingException, IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new TiffImageParser().write(baos, outputSet);
        return baos.toByteArray();
    }

    private boolean hasAlphaChannel(final BufferedImage src) {
        return src.getColorModel().hasAlpha();
    }

    private byte[] encodeVp8l(final BufferedImage src, final int width, final int height, final boolean hasAlpha, final int compressionLevel)
            throws ImagingException, IOException {
        final byte[] pixelData = extractPixelData(src, width, height, hasAlpha);

        final byte[] compressedData = compressImageData(pixelData, compressionLevel);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(VP8L_SIGNATURE);

        final int widthMinus1 = width - 1;
        final int heightMinus1 = height - 1;

        baos.write(widthMinus1 & 0xFF);
        baos.write(((widthMinus1 >> 8) & 0x3F) | ((heightMinus1 & 0x03) << 6));
        baos.write((heightMinus1 >> 2) & 0xFF);
        baos.write(((heightMinus1 >> 10) & 0x0F) | ((hasAlpha ? 1 : 0) << 4) | (VP8L_VERSION << 5));

        baos.write(compressedData);

        return baos.toByteArray();
    }

    private byte[] extractPixelData(final BufferedImage src, final int width, final int height, final boolean hasAlpha) {
        final int bytesPerPixel = hasAlpha ? 4 : 3;
        final byte[] pixelData = new byte[width * height * bytesPerPixel];
        int idx = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int rgb = src.getRGB(x, y);
                pixelData[idx++] = (byte) ((rgb >> 16) & 0xFF);
                pixelData[idx++] = (byte) ((rgb >> 8) & 0xFF);
                pixelData[idx++] = (byte) (rgb & 0xFF);
                if (hasAlpha) {
                    pixelData[idx++] = (byte) ((rgb >>> 24) & 0xFF);
                }
            }
        }

        return pixelData;
    }

    private byte[] compressImageData(final byte[] pixelData, final int compressionLevel) throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DeflaterOutputStream dos = new DeflaterOutputStream(baos, new Deflater(compressionLevel))) {
            dos.write(pixelData);
        }
        return baos.toByteArray();
    }

    private void writeWebPFile(final OutputStream os, final byte[] vp8lData, final byte[] exifData, final String xmpXml) throws IOException {
        final int vp8lChunkSize = vp8lData.length;
        final int exifChunkSize = exifData != null ? exifData.length + (exifData.length % 2 != 0 ? 1 : 0) : 0;
        final byte[] xmpBytes = xmpXml != null ? xmpXml.getBytes(StandardCharsets.UTF_8) : null;
        final int xmpChunkSize = xmpBytes != null ? xmpBytes.length + (xmpBytes.length % 2 != 0 ? 1 : 0) : 0;

        final int fileSize = 4 + 8 + vp8lChunkSize + (exifData != null ? 8 + exifChunkSize : 0) + (xmpBytes != null ? 8 + xmpChunkSize : 0) - 4;

        final AbstractBinaryOutputStream bos = new LittleEndianBinaryOutputStream(os);

        bos.write("RIFF".getBytes(StandardCharsets.US_ASCII));
        bos.write4Bytes(fileSize);
        bos.write("WEBP".getBytes(StandardCharsets.US_ASCII));

        bos.write("VP8L".getBytes(StandardCharsets.US_ASCII));
        bos.write4Bytes(vp8lChunkSize);
        bos.write(vp8lData);

        if (exifData != null) {
            bos.write("EXIF".getBytes(StandardCharsets.US_ASCII));
            bos.write4Bytes(exifData.length);
            bos.write(exifData);
            if (exifData.length % 2 != 0) {
                bos.write(0);
            }
        }

        if (xmpBytes != null) {
            bos.write("XMP ".getBytes(StandardCharsets.US_ASCII));
            bos.write4Bytes(xmpBytes.length);
            bos.write(xmpBytes);
            if (xmpBytes.length % 2 != 0) {
                bos.write(0);
            }
        }

        bos.flush();
    }
}
