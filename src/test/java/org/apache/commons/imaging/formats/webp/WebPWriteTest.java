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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.commons.imaging.ImageFormats;
import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputField;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the WebP write functionality.
 */
class WebPWriteTest extends AbstractWebPTest {

    @TempDir
    File tempDir;

    @Test
    void testWriteAndReadRgbImage() throws Exception {
        final BufferedImage src = createTestImage(100, 50, false);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final WebPImagingParameters params = new WebPImagingParameters();
        params.setCompressionLevel(6);
        Imaging.writeImage(src, baos, ImageFormats.WEBP, params);

        final byte[] webpData = baos.toByteArray();
        assertNotNull(webpData);
        assertTrue(webpData.length > 0);

        final BufferedImage read = Imaging.getBufferedImage(webpData);
        assertNotNull(read);
        assertEquals(src.getWidth(), read.getWidth());
        assertEquals(src.getHeight(), read.getHeight());

        assertImagesEqual(src, read);
    }

    @Test
    void testWriteAndReadRgbaImage() throws Exception {
        final BufferedImage src = createTestImage(80, 60, true);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final WebPImagingParameters params = new WebPImagingParameters();
        params.setCompressionLevel(4);
        Imaging.writeImage(src, baos, ImageFormats.WEBP, params);

        final byte[] webpData = baos.toByteArray();
        assertNotNull(webpData);
        assertTrue(webpData.length > 0);

        final BufferedImage read = Imaging.getBufferedImage(webpData);
        assertNotNull(read);
        assertEquals(src.getWidth(), read.getWidth());
        assertEquals(src.getHeight(), read.getHeight());
    }

    @Test
    void testWriteWithDifferentCompressionLevels() throws Exception {
        final BufferedImage src = createTestImage(50, 50, false);

        for (int level = WebPImagingParameters.MIN_COMPRESSION_LEVEL; level <= WebPImagingParameters.MAX_COMPRESSION_LEVEL; level++) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final WebPImagingParameters params = new WebPImagingParameters();
            params.setCompressionLevel(level);
            Imaging.writeImage(src, baos, ImageFormats.WEBP, params);

            final byte[] webpData = baos.toByteArray();
            assertNotNull(webpData);
            assertTrue(webpData.length > 0);

            final BufferedImage read = Imaging.getBufferedImage(webpData);
            assertNotNull(read);
            assertEquals(src.getWidth(), read.getWidth());
            assertEquals(src.getHeight(), read.getHeight());
        }
    }

    @Test
    void testWriteWithXmpMetadata() throws Exception {
        final BufferedImage src = createTestImage(30, 30, false);

        final String xmpXml = "<?xml version=\"1.0\"?><x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"><rdf:Description rdf:about=\"\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>Test Image</dc:title></rdf:Description></rdf:RDF></x:xmpmeta>";

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final WebPImagingParameters params = new WebPImagingParameters();
        params.setXmpXml(xmpXml);
        Imaging.writeImage(src, baos, ImageFormats.WEBP, params);

        final byte[] webpData = baos.toByteArray();
        assertNotNull(webpData);

        final String readXmp = Imaging.getXmpXml(ByteSource.array(webpData));
        assertNotNull(readXmp);
        assertTrue(readXmp.contains("Test Image"));
    }

    @Test
    void testWriteWithExifMetadata() throws Exception {
        final BufferedImage src = createTestImage(40, 40, false);

        final TiffOutputSet outputSet = new TiffOutputSet();
        final TiffOutputDirectory exifDirectory = outputSet.getOrCreateExifDirectory();
        exifDirectory.add(new TiffOutputField(TiffTagConstants.TIFF_TAG_MAKE, "TestCamera".getBytes()));

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final WebPImagingParameters params = new WebPImagingParameters();
        params.setTiffOutputSet(outputSet);
        Imaging.writeImage(src, baos, ImageFormats.WEBP, params);

        final byte[] webpData = baos.toByteArray();
        assertNotNull(webpData);

        final WebPImageMetadata metadata = new WebPImageParser().getMetadata(ByteSource.array(webpData));
        assertNotNull(metadata);
    }

    @Test
    void testWriteToFileAndReadBack() throws Exception {
        final BufferedImage src = createTestImage(64, 64, true);
        final File outputFile = new File(tempDir, "test_output.webp");

        final WebPImagingParameters params = new WebPImagingParameters();
        params.setCompressionLevel(5);
        Imaging.writeImage(src, outputFile, ImageFormats.WEBP, params);

        assertTrue(outputFile.exists());
        assertTrue(outputFile.length() > 0);

        final ImageInfo imageInfo = Imaging.getImageInfo(outputFile);
        assertNotNull(imageInfo);
        assertEquals(64, imageInfo.getWidth());
        assertEquals(64, imageInfo.getHeight());
        assertEquals(ImageFormats.WEBP, imageInfo.getFormat());

        final BufferedImage read = Imaging.getBufferedImage(outputFile);
        assertNotNull(read);
        assertEquals(src.getWidth(), read.getWidth());
        assertEquals(src.getHeight(), read.getHeight());
    }

    @Test
    void testInvalidCompressionLevel() {
        final WebPImagingParameters params = new WebPImagingParameters();
        assertThrows(IllegalArgumentException.class, () -> params.setCompressionLevel(0));
        assertThrows(IllegalArgumentException.class, () -> params.setCompressionLevel(10));
    }

    @Test
    void testWriteNullImage() throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertThrows(ImagingException.class, () -> Imaging.writeImage(null, baos, ImageFormats.WEBP));
    }

    @Test
    void testWriteLargeImage() throws Exception {
        final BufferedImage src = createTestImage(500, 500, false);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final WebPImagingParameters params = new WebPImagingParameters();
        params.setCompressionLevel(3);
        Imaging.writeImage(src, baos, ImageFormats.WEBP, params);

        final byte[] webpData = baos.toByteArray();
        assertNotNull(webpData);

        final BufferedImage read = Imaging.getBufferedImage(webpData);
        assertNotNull(read);
        assertEquals(500, read.getWidth());
        assertEquals(500, read.getHeight());
    }

    @Test
    void testWriteAndReadRoundTrip() throws Exception {
        final BufferedImage src = createGradientImage(128, 128, false);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Imaging.writeImage(src, baos, ImageFormats.WEBP);

        final ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        final BufferedImage read = Imaging.getBufferedImage(bais);
        assertNotNull(read);
        assertEquals(src.getWidth(), read.getWidth());
        assertEquals(src.getHeight(), read.getHeight());

        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                assertEquals(src.getRGB(x, y), read.getRGB(x, y), "Pixel mismatch at (" + x + "," + y + ")");
            }
        }
    }

    private BufferedImage createTestImage(final int width, final int height, final boolean hasAlpha) {
        final int imageType = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        final BufferedImage image = new BufferedImage(width, height, imageType);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int red = (x * 255) / width;
                final int green = (y * 255) / height;
                final int blue = 128;
                final int alpha = hasAlpha ? 200 : 255;
                if (hasAlpha) {
                    image.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
                } else {
                    image.setRGB(x, y, (red << 16) | (green << 8) | blue);
                }
            }
        }

        return image;
    }

    private BufferedImage createGradientImage(final int width, final int height, final boolean hasAlpha) {
        final int imageType = hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        final BufferedImage image = new BufferedImage(width, height, imageType);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int red = (x * 255) / (width - 1);
                final int green = (y * 255) / (height - 1);
                final int blue = 255 - red;
                final int alpha = hasAlpha ? 255 : 255;
                image.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }

        return image;
    }

    private void assertImagesEqual(final BufferedImage expected, final BufferedImage actual) {
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());

        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                assertEquals(expected.getRGB(x, y), actual.getRGB(x, y), "Pixel mismatch at (" + x + "," + y + ")");
            }
        }
    }
}
