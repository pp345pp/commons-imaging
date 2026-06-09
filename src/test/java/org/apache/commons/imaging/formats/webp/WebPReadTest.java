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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.webp.chunks.WebPChunkIccp;
import org.apache.commons.imaging.internal.Debug;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests that read WebP images.
 */
class WebPReadTest extends AbstractWebPTest {

    private static byte[] createWebPWithIccProfile(final byte[] iccProfileBytes) throws IOException {
        final byte[] vp8xPayload = new byte[10];
        final ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.write("WEBP".getBytes(StandardCharsets.US_ASCII));
        writeChunk(file, "VP8X", vp8xPayload);
        writeChunk(file, "ICCP", iccProfileBytes);

        final byte[] payload = file.toByteArray();
        final ByteArrayOutputStream webp = new ByteArrayOutputStream();
        webp.write("RIFF".getBytes(StandardCharsets.US_ASCII));
        writeLittleEndianInt(webp, payload.length);
        webp.write(payload);
        return webp.toByteArray();
    }

    private static void writeChunk(final ByteArrayOutputStream os, final String type, final byte[] data) throws IOException {
        os.write(type.getBytes(StandardCharsets.US_ASCII));
        writeLittleEndianInt(os, data.length);
        os.write(data);
        if ((data.length & 1) != 0) {
            os.write(0);
        }
    }

    private static void writeLittleEndianInt(final ByteArrayOutputStream os, final int value) {
        os.write(value & 0xff);
        os.write(value >>> 8 & 0xff);
        os.write(value >>> 16 & 0xff);
        os.write(value >>> 24 & 0xff);
    }

    /**
     * Not implemented yet.
     *
     * @throws IOException if it failed to read the image.
     */
    @Test
    void testBufferedImageNotSupported() throws IOException {
        final File emptyWebP = new File(WebPReadTest.class.getResource("/images/webp/empty/empty-100x100.webp").getFile());
        final WebPImageParser parser = new WebPImageParser();
        final ImagingException exception = assertThrows(ImagingException.class,
                () -> parser.getBufferedImage(ByteSource.file(emptyWebP), parser.getDefaultParameters()));
        assertTrue(exception.getMessage().contains("Reading WebP files is currently not supported"));
    }

    /**
     * Basic features of the parser.
     */
    @Test
    void testParser() {
        final WebPImageParser parser = new WebPImageParser();
        assertEquals("WebP-Custom", parser.getName());
        assertEquals("webp", parser.getDefaultExtension());
    }

    /**
     * @param imageFile parameterized test image.
     * @throws Exception if it cannot open the images.
     */
    @ParameterizedTest
    @MethodSource("images")
    void testRead(final File imageFile) throws Exception {
        Debug.debug("start");

        Debug.debug("imageFile", imageFile);

        final ImageMetadata metadata = Imaging.getMetadata(imageFile);
        assertFalse(metadata instanceof File);

        final ImageInfo imageInfo = Imaging.getImageInfo(imageFile);
        assertNotNull(imageInfo);

        Debug.debug("ICC profile", Imaging.getIccProfileBytes(imageFile));
    }

    @Test
    void testRepairsMismatchedIccProfileSizeInWebPChunk() throws Exception {
        final byte[] iccProfileBytes = ICC_Profile.getInstance(ColorSpace.CS_LINEAR_RGB).getData();
        final byte[] malformedIccProfileBytes = iccProfileBytes.clone();
        malformedIccProfileBytes[0] = 0;
        malformedIccProfileBytes[1] = 0;
        malformedIccProfileBytes[2] = 0;
        malformedIccProfileBytes[3] = 1;

        final byte[] webpBytes = createWebPWithIccProfile(malformedIccProfileBytes);
        final byte[] repairedIccProfileBytes = Imaging.getIccProfileBytes(webpBytes);
        assertArrayEquals(iccProfileBytes, repairedIccProfileBytes);
        assertNotNull(Imaging.getIccProfile(webpBytes));
    }

    /**
     * Test that the given size, and the byte array length match.
     */
    @Test
    void testWebPChunkInvalidSizeBytes() {
        final ImagingException exception = assertThrows(ImagingException.class, () -> new WebPChunkIccp(0, 10, new byte[] {}));
        assertEquals("Chunk size must match bytes length", exception.getMessage());
    }
}
