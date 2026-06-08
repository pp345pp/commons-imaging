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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WebPWriteTest {

    @TempDir
    Path tempDir;

    @Test
    void testCompressionLevelValidation() {
        final WebPImagingParameters params = new WebPImagingParameters();
        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> params.setCompressionLevel(0));
        assertEquals("Compression level must be between 1 and 9", exception.getMessage());
    }

    @Test
    void testWriteRgbRoundTrip() throws Exception {
        final BufferedImage source = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        source.setRGB(0, 0, 0xff112233);
        source.setRGB(1, 0, 0xff445566);
        source.setRGB(2, 0, 0xff778899);
        source.setRGB(0, 1, 0xff99aabb);
        source.setRGB(1, 1, 0xffccddee);
        source.setRGB(2, 1, 0xff010203);

        final Path output = tempDir.resolve("rgb.webp");
        final WebPImageParser parser = new WebPImageParser();
        final WebPImagingParameters params = new WebPImagingParameters().setCompressionLevel(4);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            parser.writeImage(source, outputStream, params);
            Files.write(output, outputStream.toByteArray());
        }

        final BufferedImage decoded = Imaging.getBufferedImage(output.toFile());
        assertImagesEqual(source, decoded);

        final ImageInfo imageInfo = Imaging.getImageInfo(output.toFile());
        assertEquals(24, imageInfo.getBitsPerPixel());
        assertEquals(3, imageInfo.getWidth());
        assertEquals(2, imageInfo.getHeight());
    }

    @Test
    void testWriteRgbaWithMetadataRoundTrip() throws Exception {
        final BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0x80112233);
        source.setRGB(1, 0, 0xff445566);
        source.setRGB(0, 1, 0x40445566);
        source.setRGB(1, 1, 0x00010203);

        final TiffOutputSet outputSet = new TiffOutputSet();
        final TiffOutputDirectory rootDirectory = outputSet.getOrCreateRootDirectory();
        rootDirectory.add(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION, "webp-exif");

        final String xmpXml = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"><rdf:Description rdf:about=\"\"><dc:title xmlns:dc=\"http://purl.org/dc/elements/1.1/\">webp-xmp</dc:title></rdf:Description></rdf:RDF></x:xmpmeta>";
        final WebPImagingParameters params = new WebPImagingParameters().setCompressionLevel(9).setOutputSet(outputSet).setXmpXml(xmpXml);

        final Path output = tempDir.resolve("rgba-metadata.webp");
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            new WebPImageWriter().writeImage(source, outputStream, params);
            Files.write(output, outputStream.toByteArray());
        }

        final BufferedImage decoded = Imaging.getBufferedImage(output.toFile());
        assertImagesEqual(source, decoded);

        final WebPImageMetadata metadata = (WebPImageMetadata) Imaging.getMetadata(output.toFile());
        assertNotNull(metadata);
        assertNotNull(metadata.getExif());

        final TiffField imageDescription = metadata.getExif().findField(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION);
        assertNotNull(imageDescription);
        assertEquals("webp-exif", imageDescription.getStringValue());
        assertEquals(xmpXml, Imaging.getXmpXml(output.toFile()));

        final ImageInfo imageInfo = Imaging.getImageInfo(output.toFile());
        assertEquals(32, imageInfo.getBitsPerPixel());
        assertEquals(2, imageInfo.getWidth());
        assertEquals(2, imageInfo.getHeight());
    }

    private void assertImagesEqual(final BufferedImage expected, final BufferedImage actual) {
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                assertEquals(expected.getRGB(x, y), actual.getRGB(x, y), x + "," + y);
            }
        }
    }
}
