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

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.util.stream.Stream;

import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImagingFormatException;
import org.apache.commons.imaging.formats.AbstractRoundtripTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class JpegRoundtripTest extends AbstractJpegTest {

    public static Stream<File> data() throws Exception {
        return getJpegImages().stream();
    }

    @Test
    void testCreateAndWriteSimpleImage() throws Exception {
        final int width = 100;
        final int height = 100;
        final BufferedImage srcImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int r = (x * 255) / width;
                final int g = (y * 255) / height;
                final int b = ((x + y) * 127) / (width + height);
                final int argb = (0xff << 24) | (r << 16) | (g << 8) | b;
                srcImage.setRGB(x, y, argb);
            }
        }

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Imaging.writeImage(srcImage, baos, org.apache.commons.imaging.ImageFormats.JPEG);

        final byte[] jpegBytes = baos.toByteArray();
        final BufferedImage resultImage = Imaging.getBufferedImage(jpegBytes);
        assertNotNull(resultImage);
        assertEquals(width, resultImage.getWidth());
        assertEquals(height, resultImage.getHeight());
    }

    @Test
    void testCreateAndWriteWithDifferentQualities() throws Exception {
        final int width = 50;
        final int height = 50;
        final BufferedImage srcImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                srcImage.setRGB(x, y, 0xffff0000);
            }
        }

        final float[] qualities = { 0.2f, 0.5f, 0.8f, 1.0f };
        for (final float quality : qualities) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final JpegImagingParameters params = new JpegImagingParameters();
            params.setQuality(quality);

            new JpegImageParser().writeImage(srcImage, baos, params);

            final byte[] jpegBytes = baos.toByteArray();
            final BufferedImage resultImage = Imaging.getBufferedImage(jpegBytes);
            assertNotNull(resultImage);
        }
    }

}
