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
package org.apache.commons.imaging.formats.gif;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.test.TestResources;
import org.junit.jupiter.api.Test;

class GifImageParserAnimatedTest {

    @Test
    void testAllFramesAnimatedGifTwoFrames() throws ImagingException, IOException {
        final File file = TestResources.resourceToFile("/data/images/gif/animated/2/no_disposal.gif");
        assertNotNull(file);
        assertTrue(file.exists());

        final List<BufferedImage> images = Imaging.getAllBufferedImages(file);
        assertEquals(2, images.size(), "getAllBufferedImages should return all 2 frames");

        final ImageMetadata metadata = Imaging.getMetadata(file);
        assertNotNull(metadata);
        final GifImageMetadata gifMetadata = (GifImageMetadata) metadata;
        assertEquals(2, gifMetadata.getItems().size(), "Metadata should contain entries for all 2 frames");

        for (int i = 0; i < 2; i++) {
            final GifImageMetadataItem item = gifMetadata.getItems().get(i);
            assertNotNull(item);
            assertTrue(item.getDelay() > 0, "Frame " + i + " should have non-zero delay");
        }

        final BufferedImage firstImage = images.get(0);
        final BufferedImage lastImage = images.get(1);
        assertNotNull(firstImage);
        assertNotNull(lastImage);
        assertEquals(firstImage.getWidth(), lastImage.getWidth());
        assertEquals(firstImage.getHeight(), lastImage.getHeight());

        final int firstPixel = firstImage.getRGB(0, 0);
        final int lastPixel = lastImage.getRGB(0, 0);
        final boolean hasPixelDifference = firstPixel != lastPixel;
        assertTrue(hasPixelDifference, "First and last frame should have pixel difference in animated GIF");
    }

    @Test
    void testMetadataRetainsDelayDisposalTransparency() throws ImagingException, IOException {
        final File file = TestResources.resourceToFile("/data/images/gif/animated/2/no_disposal.gif");
        assertNotNull(file);

        final ImageMetadata metadata = Imaging.getMetadata(file);
        final GifImageMetadata gifMetadata = (GifImageMetadata) metadata;

        for (final GifImageMetadataItem item : gifMetadata.getItems()) {
            assertNotNull(item.getDisposalMethod());
            assertTrue(item.getDelay() >= 0);
            assertTrue(item.getTransparentColorIndex() >= -1);
        }
    }

    @Test
    void testSingleFrameGif() throws ImagingException, IOException {
        final File file = TestResources.resourceToFile("/data/images/gif/single/1/Oregon Scientific DS6639 - DSC_0307 - small.gif");
        assertNotNull(file);
        assertTrue(file.exists());

        final List<BufferedImage> images = Imaging.getAllBufferedImages(file);
        assertEquals(1, images.size(), "Single frame GIF should still return only one frame");

        final ImageMetadata metadata = Imaging.getMetadata(file);
        assertNotNull(metadata);
        final GifImageMetadata gifMetadata = (GifImageMetadata) metadata;
        assertEquals(1, gifMetadata.getItems().size());

        final GifImageMetadataItem item = gifMetadata.getItems().get(0);
        assertNotNull(item);
        assertEquals(-1, item.getTransparentColorIndex());
    }
}