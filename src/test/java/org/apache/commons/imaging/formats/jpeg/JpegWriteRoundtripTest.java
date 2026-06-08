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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;

import org.apache.commons.imaging.ImageFormats;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.junit.jupiter.api.Test;

class JpegWriteRoundtripTest {

    private static final byte[] ICC_PROFILE_LABEL = { 0x49, 0x43, 0x43, 0x5F, 0x50, 0x52, 0x4F, 0x46, 0x49, 0x4C, 0x45, 0x00 };

    private byte[] addExif(final byte[] jpegBytes, final String description) throws Exception {
        final TiffOutputSet outputSet = new TiffOutputSet();
        final TiffOutputDirectory rootDirectory = outputSet.getOrCreateRootDirectory();
        rootDirectory.add(TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION, description);

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        new ExifRewriter().updateExifMetadataLossy(jpegBytes, byteArrayOutputStream, outputSet);
        return byteArrayOutputStream.toByteArray();
    }

    private byte[] addIccProfile(final byte[] jpegBytes, final byte[] iccProfile) throws IOException {
        final byte[] app2Segment = createIccApp2Segment(iccProfile);
        final int insertOffset = findAppInsertOffset(jpegBytes);
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(jpegBytes.length + app2Segment.length);
        byteArrayOutputStream.write(jpegBytes, 0, insertOffset);
        byteArrayOutputStream.write(app2Segment);
        byteArrayOutputStream.write(jpegBytes, insertOffset, jpegBytes.length - insertOffset);
        return byteArrayOutputStream.toByteArray();
    }

    private void assertImagesEqual(final BufferedImage expected, final BufferedImage actual) {
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                assertEquals(expected.getRGB(x, y), actual.getRGB(x, y), "pixel mismatch at (" + x + ',' + y + ')');
            }
        }
    }

    private void assertImagesWithinTolerance(final BufferedImage expected, final BufferedImage actual, final int tolerance) {
        assertEquals(expected.getWidth(), actual.getWidth());
        assertEquals(expected.getHeight(), actual.getHeight());

        int maxDelta = 0;
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                final int expectedRgb = expected.getRGB(x, y);
                final int actualRgb = actual.getRGB(x, y);
                maxDelta = Math.max(maxDelta, channelDelta(expectedRgb >>> 16, actualRgb >>> 16));
                maxDelta = Math.max(maxDelta, channelDelta(expectedRgb >>> 8, actualRgb >>> 8));
                maxDelta = Math.max(maxDelta, channelDelta(expectedRgb, actualRgb));
            }
        }
        assertTrue(maxDelta <= tolerance, "maximum channel delta was " + maxDelta);
    }

    private int channelDelta(final int expected, final int actual) {
        return Math.abs((expected & 0xff) - (actual & 0xff));
    }

    private BufferedImage createGrayFixture() {
        final BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_BYTE_GRAY);
        fillBlock(image, 0, 0, 8, 8, new Color(0, 0, 0));
        fillBlock(image, 8, 0, 8, 8, new Color(64, 64, 64));
        fillBlock(image, 0, 8, 8, 8, new Color(160, 160, 160));
        fillBlock(image, 8, 8, 8, 8, new Color(255, 255, 255));
        return image;
    }

    private byte[] createIccApp2Segment(final byte[] iccProfile) throws IOException {
        final int length = 2 + ICC_PROFILE_LABEL.length + 2 + iccProfile.length;
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(length + 2);
        byteArrayOutputStream.write(0xff);
        byteArrayOutputStream.write(0xe2);
        byteArrayOutputStream.write(length >>> 8 & 0xff);
        byteArrayOutputStream.write(length & 0xff);
        byteArrayOutputStream.write(ICC_PROFILE_LABEL);
        byteArrayOutputStream.write(1);
        byteArrayOutputStream.write(1);
        byteArrayOutputStream.write(iccProfile);
        return byteArrayOutputStream.toByteArray();
    }

    private BufferedImage createRgbFixture() {
        final BufferedImage image = new BufferedImage(32, 16, BufferedImage.TYPE_INT_RGB);
        fillBlock(image, 0, 0, 8, 8, Color.RED);
        fillBlock(image, 8, 0, 8, 8, Color.GREEN);
        fillBlock(image, 16, 0, 8, 8, Color.BLUE);
        fillBlock(image, 24, 0, 8, 8, Color.YELLOW);
        fillBlock(image, 0, 8, 8, 8, Color.CYAN);
        fillBlock(image, 8, 8, 8, 8, Color.MAGENTA);
        fillBlock(image, 16, 8, 8, 8, Color.BLACK);
        fillBlock(image, 24, 8, 8, 8, Color.WHITE);
        return image;
    }

    private int findAppInsertOffset(final byte[] jpegBytes) {
        int offset = 2;
        int insertOffset = 2;
        while (offset + 4 <= jpegBytes.length) {
            final int marker = (jpegBytes[offset] & 0xff) << 8 | jpegBytes[offset + 1] & 0xff;
            if (marker == JpegConstants.SOS_MARKER || marker == JpegConstants.EOI_MARKER) {
                break;
            }
            final int segmentLength = (jpegBytes[offset + 2] & 0xff) << 8 | jpegBytes[offset + 3] & 0xff;
            final int nextOffset = offset + 2 + segmentLength;
            if (marker >= JpegConstants.JPEG_APP0_MARKER && marker <= JpegConstants.JPEG_APP15_MARKER) {
                insertOffset = nextOffset;
            }
            offset = nextOffset;
        }
        return insertOffset;
    }

    private void fillBlock(final BufferedImage image, final int startX, final int startY, final int width, final int height, final Color color) {
        for (int y = startY; y < startY + height; y++) {
            for (int x = startX; x < startX + width; x++) {
                image.setRGB(x, y, color.getRGB());
            }
        }
    }

    private byte[] writeSourceJpeg(final BufferedImage image) throws IOException {
        final Iterator<ImageWriter> imageWriters = ImageIO.getImageWritersByFormatName("jpeg");
        final ImageWriter imageWriter = imageWriters.next();
        final JPEGImageWriteParam writeParam = new JPEGImageWriteParam(null);
        writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        writeParam.setCompressionQuality(1f);
        writeParam.setProgressiveMode(ImageWriteParam.MODE_DISABLED);

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(byteArrayOutputStream)) {
            imageWriter.setOutput(imageOutputStream);
            imageWriter.write(null, new IIOImage(image, null, null), writeParam);
        } finally {
            imageWriter.dispose();
        }
        return byteArrayOutputStream.toByteArray();
    }

    @Test
    void roundTripGrayJpegPreservesPixels() throws Exception {
        final byte[] sourceJpeg = writeSourceJpeg(createGrayFixture());
        final BufferedImage decoded = Imaging.getBufferedImage(sourceJpeg);
        final byte[] rewritten = Imaging.writeImageToBytes(decoded, ImageFormats.JPEG, new JpegImagingParameters().setQuality(100));
        final BufferedImage roundTripped = Imaging.getBufferedImage(rewritten);

        assertImagesEqual(decoded, roundTripped);
    }

    @Test
    void roundTripPreservesExifAndIccProfile() throws Exception {
        final byte[] sourceWithExif = addExif(writeSourceJpeg(createRgbFixture()), "jpeg-roundtrip");
        final byte[] sourceWithMetadata = addIccProfile(sourceWithExif, ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData());
        final BufferedImage decoded = Imaging.getBufferedImage(sourceWithMetadata);

        final JpegImagingParameters params = new JpegImagingParameters().setQuality(100).setMetadataSource(sourceWithMetadata);
        final byte[] rewritten = Imaging.writeImageToBytes(decoded, ImageFormats.JPEG, params);
        final BufferedImage roundTripped = Imaging.getBufferedImage(rewritten);
        final JpegImageParser parser = new JpegImageParser();

        assertImagesWithinTolerance(decoded, roundTripped, 4);
        assertArrayEquals(parser.getExifRawData(ByteSource.array(sourceWithMetadata)), parser.getExifRawData(ByteSource.array(rewritten)));
        assertArrayEquals(Imaging.getIccProfileBytes(sourceWithMetadata), Imaging.getIccProfileBytes(rewritten));
        assertNotNull(Imaging.getMetadata(rewritten));
    }

    @Test
    void roundTripRgbJpegPreservesPixelsWithinTolerance() throws Exception {
        final byte[] sourceJpeg = writeSourceJpeg(createRgbFixture());
        final BufferedImage decoded = Imaging.getBufferedImage(sourceJpeg);
        final byte[] rewritten = Imaging.writeImageToBytes(decoded, ImageFormats.JPEG, new JpegImagingParameters().setQuality(100));
        final BufferedImage roundTripped = Imaging.getBufferedImage(rewritten);

        assertImagesWithinTolerance(decoded, roundTripped, 4);
    }
}
