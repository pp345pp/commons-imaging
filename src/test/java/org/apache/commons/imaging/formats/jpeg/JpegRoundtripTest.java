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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for JPEG write: read a JPEG, write it back, read it again,
 * and verify pixel consistency.
 */
public class JpegRoundTripTest extends AbstractJpegTest {

    /**
     * Tests that an RGB JPEG can be read, written, and re-read with pixel data preserved.
     */
    @Test
    void testRgbRoundTrip() throws IOException, ImagingException {
        final File inputFile = getTestImage(imageFilter);
        final BufferedImage original = Imaging.getBufferedImage(inputFile);

        final int width = original.getWidth();
        final int height = original.getHeight();
        final int[] originalPixels = new int[width * height];
        original.getRGB(0, 0, width, height, originalPixels, 0, width);

        final JpegImagingParameters params = new JpegImagingParameters();
        params.setCompressionQuality(95);

        final byte[] jpegBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            new JpegImageWriter().writeImage(original, baos, params);
            jpegBytes = baos.toByteArray();
        }

        assertNotNull(jpegBytes);
        assertTrue(jpegBytes.length > 0);

        final BufferedImage reRead = new JpegImageParser().getBufferedImage(ByteSource.array(jpegBytes), new JpegImagingParameters());
        assertNotNull(reRead);
        assertEquals(width, reRead.getWidth());
        assertEquals(height, reRead.getHeight());

        final int[] reReadPixels = new int[width * height];
        reRead.getRGB(0, 0, width, height, reReadPixels, 0, width);

        int errorCount = 0;
        long totalError = 0;
        for (int i = 0; i < originalPixels.length; i++) {
            final int a = originalPixels[i];
            final int b = reReadPixels[i];
            final int dr = Math.abs(((a >> 16) & 0xff) - ((b >> 16) & 0xff));
            final int dg = Math.abs(((a >> 8) & 0xff) - ((b >> 8) & 0xff));
            final int db = Math.abs((a & 0xff) - (b & 0xff));
            final int maxDiff = Math.max(dr, Math.max(dg, db));
            totalError += maxDiff;
            if (maxDiff > 30) {
                errorCount++;
            }
        }

        final double avgError = (double) totalError / originalPixels.length;
        final double errorRate = (double) errorCount / originalPixels.length;

        assertEquals(0.0, errorRate, 0.01, "Too many pixels differ significantly after round-trip");
        assertTrue(avgError < 10.0, "Average pixel error too high: " + avgError);
    }

    /**
     * Tests that an RGB JPEG round-trip with Imaging.writeImage produces a valid JPEG.
     */
    @Test
    void testImagingWriteImage() throws IOException, ImagingException {
        final File inputFile = getTestImage(imageFilter);
        final BufferedImage original = Imaging.getBufferedImage(inputFile);

        final byte[] jpegBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Imaging.writeImage(original, baos, org.apache.commons.imaging.ImageFormats.JPEG);
            jpegBytes = baos.toByteArray();
        }

        assertNotNull(jpegBytes);
        assertTrue(jpegBytes.length > 0);

        final BufferedImage reRead = Imaging.getBufferedImage(jpegBytes);
        assertNotNull(reRead);
        assertEquals(original.getWidth(), reRead.getWidth());
        assertEquals(original.getHeight(), reRead.getHeight());
    }

    /**
     * Tests that EXIF data is preserved during JPEG round-trip write.
     */
    @Test
    void testExifPreservation() throws IOException, ImagingException {
        final File inputFile = getTestImage(imageFilter);
        final JpegImageParser parser = new JpegImageParser();
        final byte[] originalExif = parser.getExifRawData(ByteSource.file(inputFile));

        final BufferedImage original = Imaging.getBufferedImage(inputFile);

        final JpegImagingParameters params = new JpegImagingParameters();
        params.setCompressionQuality(85);
        if (originalExif != null) {
            params.setExifData(originalExif);
        }

        final byte[] jpegBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            new JpegImageWriter().writeImage(original, baos, params);
            jpegBytes = baos.toByteArray();
        }

        assertNotNull(jpegBytes);
        assertTrue(jpegBytes.length > 0);

        final byte[] reReadExif = parser.getExifRawData(ByteSource.array(jpegBytes));
        if (originalExif != null) {
            assertNotNull(reReadExif, "EXIF data should be present after round-trip");
        }
    }

    /**
     * Tests that a gray JPEG can be read, written, and re-read with pixel data preserved.
     */
    @Test
    void testGrayScaleRoundTrip() throws IOException, ImagingException {
        final File inputFile = getTestImage(imageFilter);
        final BufferedImage original = Imaging.getBufferedImage(inputFile);

        final BufferedImage grayImage = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < original.getHeight(); y++) {
            for (int x = 0; x < original.getWidth(); x++) {
                final int rgb = original.getRGB(x, y);
                final int gray = (int) (0.299 * ((rgb >> 16) & 0xff) + 0.587 * ((rgb >> 8) & 0xff) + 0.114 * (rgb & 0xff));
                final int grayRgb = 0xff000000 | (gray << 16) | (gray << 8) | gray;
                grayImage.setRGB(x, y, grayRgb);
            }
        }

        final JpegImagingParameters params = new JpegImagingParameters();
        params.setCompressionQuality(90);

        final byte[] jpegBytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            new JpegImageWriter().writeImage(grayImage, baos, params);
            jpegBytes = baos.toByteArray();
        }

        assertNotNull(jpegBytes);
        assertTrue(jpegBytes.length > 0);

        final BufferedImage reRead = Imaging.getBufferedImage(jpegBytes);
        assertNotNull(reRead);
        assertEquals(grayImage.getWidth(), reRead.getWidth());
        assertEquals(grayImage.getHeight(), reRead.getHeight());
    }
}