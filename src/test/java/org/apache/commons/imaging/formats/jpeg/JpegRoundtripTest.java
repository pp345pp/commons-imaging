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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.commons.imaging.ImageFormats;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImagingException;
import org.junit.jupiter.api.Test;

class JpegRoundtripTest {

    private static final int PIXEL_TOLERANCE = 30;

    @Test
    void testRgbRoundtrip() throws ImagingException, IOException {
        final int width = 64;
        final int height = 64;
        final BufferedImage srcImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int r = x * 255 / (width - 1);
                final int g = y * 255 / (height - 1);
                final int b = ((x + y) * 255) / (width + height - 2);
                final int rgb = 0xff000000 | r << 16 | g << 8 | b;
                srcImage.setRGB(x, y, rgb);
            }
        }

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final JpegImagingParameters params = new JpegImagingParameters().setQuality(100);
        Imaging.writeImage(srcImage, baos, ImageFormats.JPEG, params);

        final byte[] jpegBytes = baos.toByteArray();
        assertNotNull(jpegBytes);
        assertTrue(jpegBytes.length > 0);

        assertEquals((byte) 0xff, jpegBytes[0]);
        assertEquals((byte) 0xd8, jpegBytes[1]);

        final BufferedImage dstImage = Imaging.getBufferedImage(new ByteArrayInputStream(jpegBytes));

        assertNotNull(dstImage);
        assertEquals(width, dstImage.getWidth());
        assertEquals(height, dstImage.getHeight());

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int srcRgb = srcImage.getRGB(x, y);
                final int dstRgb = dstImage.getRGB(x, y);

                final int srcR = srcRgb >> 16 & 0xff;
                final int srcG = srcRgb >> 8 & 0xff;
                final int srcB = srcRgb & 0xff;

                final int dstR = dstRgb >> 16 & 0xff;
                final int dstG = dstRgb >> 8 & 0xff;
                final int dstB = dstRgb & 0xff;

                assertTrue(Math.abs(srcR - dstR) <= PIXEL_TOLERANCE,
                        "R diff at (" + x + "," + y + "): src=" + srcR + " dst=" + dstR);
                assertTrue(Math.abs(srcG - dstG) <= PIXEL_TOLERANCE,
                        "G diff at (" + x + "," + y + "): src=" + srcG + " dst=" + dstG);
                assertTrue(Math.abs(srcB - dstB) <= PIXEL_TOLERANCE,
                        "B diff at (" + x + "," + y + "): src=" + srcB + " dst=" + dstB);
            }
        }
    }

    @Test
    void testGrayscaleRoundtrip() throws ImagingException, IOException {
        final int width = 32;
        final int height = 32;
        final BufferedImage srcImage = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int gray = (x + y) * 255 / (width + height - 2);
                srcImage.setRGB(x, y, 0xff000000 | gray << 16 | gray << 8 | gray);
            }
        }

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final JpegImagingParameters params = new JpegImagingParameters().setQuality(100);
        Imaging.writeImage(srcImage, baos, ImageFormats.JPEG, params);

        final byte[] jpegBytes = baos.toByteArray();
        assertNotNull(jpegBytes);
        assertTrue(jpegBytes.length > 0);

        final BufferedImage dstImage = Imaging.getBufferedImage(new ByteArrayInputStream(jpegBytes));

        assertNotNull(dstImage);
        assertEquals(width, dstImage.getWidth());
        assertEquals(height, dstImage.getHeight());

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int srcRgb = srcImage.getRGB(x, y);
                final int dstRgb = dstImage.getRGB(x, y);

                final int srcGray = srcRgb >> 16 & 0xff;
                final int dstGray = dstRgb >> 16 & 0xff;

                assertTrue(Math.abs(srcGray - dstGray) <= PIXEL_TOLERANCE,
                        "Gray diff at (" + x + "," + y + "): src=" + srcGray + " dst=" + dstGray);
            }
        }
    }
}
