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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.commons.imaging.ImageFormats;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.junit.jupiter.api.Test;

/**
 * Tests WebP writing and roundtrip.
 */
class WebPRoundtripTest extends AbstractWebPTest {

    @Test
    void testWriteReadSmallImageRGB() throws Exception {
        final BufferedImage original = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                final int r = (x * 16) & 0xFF;
                final int g = (y * 16) & 0xFF;
                final int b = ((x + y) * 8) & 0xFF;
                final int rgb = (0xFF << 24) | (r << 16) | (g << 8) | b;
                original.setRGB(x, y, rgb);
            }
        }

        final byte[] bytes = Imaging.writeImageToBytes(original, ImageFormats.WEBP);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        final BufferedImage read = Imaging.getBufferedImage(bytes);
        assertNotNull(read);
        assertEquals(original.getWidth(), read.getWidth());
        assertEquals(original.getHeight(), read.getHeight());

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                final int expected = original.getRGB(x, y);
                final int actual = read.getRGB(x, y);
                assertEquals(expected & 0xFFFFFF, actual & 0xFFFFFF, "Pixel mismatch at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void testWriteReadSmallImageRGBA() throws Exception {
        final BufferedImage original = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                final int r = (x * 16) & 0xFF;
                final int g = (y * 16) & 0xFF;
                final int b = ((x + y) * 8) & 0xFF;
                final int a = (x + y) < 16 ? 0xFF : 0x80;
                final int argb = (a << 24) | (r << 16) | (g << 8) | b;
                original.setRGB(x, y, argb);
            }
        }

        final byte[] bytes = Imaging.writeImageToBytes(original, ImageFormats.WEBP);
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        final BufferedImage read = Imaging.getBufferedImage(bytes);
        assertNotNull(read);
        assertEquals(original.getWidth(), read.getWidth());
        assertEquals(original.getHeight(), read.getHeight());

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                final int expected = original.getRGB(x, y);
                final int actual = read.getRGB(x, y);
                assertEquals(expected, actual, "Pixel mismatch at (" + x + "," + y + ")");
            }
        }
    }

    @Test
    void testWriteWithDifferentCompressionLevels() throws ImagingException, IOException {
        final BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                final int rgb = (0xFF << 24) | (x << 16) | (y << 8) | (x + y);
                image.setRGB(x, y, rgb);
            }
        }

        final WebPImageParser parser = new WebPImageParser();
        for (int level = 1; level <= 9; level++) {
            final WebPImagingParameters params = parser.getDefaultParameters();
            params.setCompressionLevel(level);

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            parser.writeImage(image, baos, params);
            final byte[] bytes = baos.toByteArray();

            assertNotNull(bytes);
            assertTrue(bytes.length > 0);

            final BufferedImage read = parser.getBufferedImage(ByteSource.array(bytes), params);
            assertNotNull(read);
            assertEquals(image.getWidth(), read.getWidth());
            assertEquals(image.getHeight(), read.getHeight());
        }
    }

    @Test
    void testWriteWithExifMetadata() throws ImagingException, IOException {
        final BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);

        final byte[] sampleExif = new byte[] {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0x00, 0x0A, 0x45, 0x78,
            0x69, 0x66, 0x00, 0x00, (byte) 0xFF, (byte) 0xD9
        };

        final WebPImagingParameters params = new WebPImagingParameters();
        params.setExifData(sampleExif);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new WebPImageParser().writeImage(image, baos, params);
        final byte[] bytes = baos.toByteArray();

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        final BufferedImage read = Imaging.getBufferedImage(bytes);
        assertNotNull(read);
        assertEquals(image.getWidth(), read.getWidth());
        assertEquals(image.getHeight(), read.getHeight());
    }

    @Test
    void testWriteWithXmpMetadata() throws ImagingException, IOException {
        final BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);

        final String sampleXmp = "<?xpacket begin=\"\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>"
                + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org.org/1999/02/22-rdf-syntax-ns#\">"
                + "</rdf:RDF></x:xmpmeta><?xpacket end=\"w\"?>";

        final WebPImagingParameters params = new WebPImagingParameters();
        params.setXmpXml(sampleXmp);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new WebPImageParser().writeImage(image, baos, params);
        final byte[] bytes = baos.toByteArray();

        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        final BufferedImage read = Imaging.getBufferedImage(bytes);
        assertNotNull(read);
        assertEquals(image.getWidth(), read.getWidth());
        assertEquals(image.getHeight(), read.getHeight());
    }

    @Test
    void testInvalidCompressionLevelThrows() {
        final WebPImagingParameters params = new WebPImagingParameters();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> params.setCompressionLevel(0));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> params.setCompressionLevel(10));
    }
}
