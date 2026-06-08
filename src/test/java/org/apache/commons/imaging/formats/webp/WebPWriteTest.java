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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.junit.jupiter.api.Test;

/**
 * WebP write tests.
 */
public class WebPWriteTest extends AbstractWebPTest {

    /**
     * Tests writing a simple RGB image.
     *
     * @throws ImagingException If an error occurs.
     * @throws IOException If an I/O error occurs.
     */
    @Test
    public void testWriteRgbImage() throws ImagingException, IOException {
        // Create a simple test image
        final int width = 100;
        final int height = 100;
        final BufferedImage src = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int red = (x * 255) / width;
                final int green = (y * 255) / height;
                final int blue = 128;
                src.setRGB(x, y, (0xFF << 24) | (red << 16) | (green << 8) | blue);
            }
        }

        // Write the image
        final WebPImageParser parser = new WebPImageParser();
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        parser.writeImage(src, baos, null);
        final byte[] webpData = baos.toByteArray();
        assertNotNull(webpData);
        assert webpData.length > 0;

        // Read it back
        final BufferedImage dst = parser.getBufferedImage(ByteSource.array(webpData), null);
        assertNotNull(dst);
        assertEquals(width, dst.getWidth());
        assertEquals(height, dst.getHeight());

        // Verify pixels
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int expectedRgb = src.getRGB(x, y);
                final int actualRgb = dst.getRGB(x, y);
                assertEquals(expectedRgb, actualRgb,
                        String.format("Pixel mismatch at (%d, %d): expected 0x%08X, got 0x%08X", x, y, expectedRgb, actualRgb));
            }
        }
    }

    /**
     * Tests writing an image with alpha channel.
     *
     * @throws ImagingException If an error occurs.
     * @throws IOException If an I/O error occurs.
     */
    @Test
    public void testWriteRgbaImage() throws ImagingException, IOException {
        final int width = 50;
        final int height = 50;
        final BufferedImage src = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // Fill with a gradient of alpha
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int alpha = (x + y) * 255 / (width + height - 2);
                final int red = (x * 255) / width;
                final int green = (y * 255) / height;
                final int blue = 255 - red;
                src.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }

        // Write with compression level 9 (max)
        final WebPImageParser parser = new WebPImageParser();
        final WebPImagingParameters params = new WebPImagingParameters().setCompressionLevel(9);
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        parser.writeImage(src, baos, params);
        final byte[] webpData = baos.toByteArray();
        assertNotNull(webpData);

        // Read back
        final BufferedImage dst = parser.getBufferedImage(ByteSource.array(webpData), null);
        assertNotNull(dst);
        assertEquals(width, dst.getWidth());
        assertEquals(height, dst.getHeight());

        // Verify pixels with alpha
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int expectedRgb = src.getRGB(x, y);
                final int actualRgb = dst.getRGB(x, y);
                assertEquals(expectedRgb, actualRgb,
                        String.format("Pixel mismatch at (%d, %d): expected 0x%08X, got 0x%08X", x, y, expectedRgb, actualRgb));
            }
        }
    }

    /**
     * Tests writing an image with EXIF data.
     *
     * @throws ImagingException If an error occurs.
     * @throws IOException If an I/O error occurs.
     */
    @Test
    public void testWriteWithExif() throws ImagingException, IOException {
        final int width = 20;
        final int height = 20;
        final BufferedImage src = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                src.setRGB(x, y, Color.RED.getRGB());
            }
        }

        final byte[] testExifData = new byte[]{0x01, 0x02, 0x03, 0x04};
        final WebPImagingParameters params = new WebPImagingParameters().setExifData(testExifData);
        final WebPImageParser parser = new WebPImageParser();
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        parser.writeImage(src, baos, params);
        final byte[] webpData = baos.toByteArray();

        // Verify we can read the EXIF data back
        final WebPImageMetadata metadata = parser.getMetadata(ByteSource.array(webpData), null);
        assertNotNull(metadata);
    }

    /**
     * Tests writing an image with XMP metadata.
     *
     * @throws ImagingException If an error occurs.
     * @throws IOException If an I/O error occurs.
     */
    @Test
    public void testWriteWithXmp() throws ImagingException, IOException {
        final int width = 20;
        final int height = 20;
        final BufferedImage src = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                src.setRGB(x, y, Color.BLUE.getRGB());
            }
        }

        final String testXmpData = "<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n" +
                "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n" +
                "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n" +
                "  <rdf:Description rdf:about=\"\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n" +
                "    <dc:title>Test Image</dc:title>\n" +
                "  </rdf:Description>\n" +
                "</rdf:RDF>\n" +
                "</x:xmpmeta>\n" +
                "<?xpacket end=\"w\"?>";

        final WebPImagingParameters params = new WebPImagingParameters().setXmpXml(testXmpData);
        final WebPImageParser parser = new WebPImageParser();
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        parser.writeImage(src, baos, params);
        final byte[] webpData = baos.toByteArray();

        // Verify we can read the XMP data back
        final String xmp = parser.getXmpXml(ByteSource.array(webpData), params);
        assertNotNull(xmp);
        assertEquals(testXmpData, xmp);
    }
}
