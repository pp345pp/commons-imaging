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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;

import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.test.TestResources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.NodeList;

class GifReadTest extends AbstractGifTest {

    private static final class ImageIoGifFrameMetadata {
        private final int delay;
        private final int leftPosition;
        private final int topPosition;
        private final boolean transparent;
        private final int transparentColorIndex;
        private final DisposalMethod disposalMethod;

        private ImageIoGifFrameMetadata(final int delay, final int leftPosition, final int topPosition, final boolean transparent,
                final int transparentColorIndex, final DisposalMethod disposalMethod) {
            this.delay = delay;
            this.leftPosition = leftPosition;
            this.topPosition = topPosition;
            this.transparent = transparent;
            this.transparentColorIndex = transparentColorIndex;
            this.disposalMethod = disposalMethod;
        }
    }

    public static Stream<File> animatedImageData() throws Exception {
        return getAnimatedGifImages().stream();
    }

    public static Stream<File> data() throws Exception {
        return getGifImages().stream();
    }

    public static Stream<File> singleImageData() throws Exception {
        return getGifImagesWithSingleImage().stream();
    }

    @ParameterizedTest
    @MethodSource("data")
    void testBufferedImage(final File imageFile) throws Exception {
        final BufferedImage image = Imaging.getBufferedImage(imageFile);
        assertNotNull(image);
    }

    @ParameterizedTest
    @MethodSource("animatedImageData")
    void testAnimatedGifMetadataMatchesImageIo(final File imageFile) throws Exception {
        final List<BufferedImage> images = Imaging.getAllBufferedImages(imageFile);
        final GifImageMetadata metadata = (GifImageMetadata) Imaging.getMetadata(imageFile);
        final List<ImageIoGifFrameMetadata> expectedFrames = getImageIoGifFrameMetadata(imageFile);

        assertEquals(expectedFrames.size(), images.size());
        assertEquals(expectedFrames.size(), metadata.getItems().size());

        for (int i = 0; i < expectedFrames.size(); i++) {
            final ImageIoGifFrameMetadata expectedFrame = expectedFrames.get(i);
            final GifImageMetadataItem actualFrame = metadata.getItems().get(i);
            assertEquals(expectedFrame.delay, actualFrame.getDelay());
            assertEquals(expectedFrame.leftPosition, actualFrame.getLeftPosition());
            assertEquals(expectedFrame.topPosition, actualFrame.getTopPosition());
            assertEquals(expectedFrame.transparent, actualFrame.isTransparent());
            assertEquals(expectedFrame.transparentColorIndex, actualFrame.getTransparentColorIndex());
            assertEquals(expectedFrame.disposalMethod, actualFrame.getDisposalMethod());
        }
    }

    @Test
    void testAnimatedGifReturnsDifferentFirstAndLastFrames() throws Exception {
        final File imageFile = TestResources.resourceToFile("/images/gif/animated/1/animated.gif");
        final List<BufferedImage> images = Imaging.getAllBufferedImages(imageFile);
        final List<ImageIoGifFrameMetadata> expectedFrames = getImageIoGifFrameMetadata(imageFile);

        assertEquals(expectedFrames.size(), images.size());
        assertTrue(images.size() > 1);
        assertTrue(imagesDiffer(images.get(0), images.get(images.size() - 1)));
    }

    @ParameterizedTest
    @MethodSource("singleImageData")
    void testBufferedImagesForSingleImageGif(final File imageFile) throws Exception {
        final BufferedImage image = Imaging.getBufferedImage(imageFile);
        final List<BufferedImage> images = Imaging.getAllBufferedImages(imageFile);
        assertEquals(1, images.size());
        assertFalse(imagesDiffer(image, images.get(0)));
    }

    @Test
    void testConvertInvalidDisposalMethodValues() {
        assertThrows(ImagingException.class, () -> GifImageParser.createDisposalMethodFromIntValue(8));
    }

    @Test
    void testConvertValidDisposalMethodValues() throws ImagingException {
        final DisposalMethod unspecified = GifImageParser.createDisposalMethodFromIntValue(0);
        final DisposalMethod doNotDispose = GifImageParser.createDisposalMethodFromIntValue(1);
        final DisposalMethod restoreToBackground = GifImageParser.createDisposalMethodFromIntValue(2);
        final DisposalMethod restoreToPrevious = GifImageParser.createDisposalMethodFromIntValue(3);
        final DisposalMethod toBeDefined1 = GifImageParser.createDisposalMethodFromIntValue(4);
        final DisposalMethod toBeDefined2 = GifImageParser.createDisposalMethodFromIntValue(5);
        final DisposalMethod toBeDefined3 = GifImageParser.createDisposalMethodFromIntValue(6);
        final DisposalMethod toBeDefined4 = GifImageParser.createDisposalMethodFromIntValue(7);
        assertEquals(unspecified, DisposalMethod.UNSPECIFIED);
        assertEquals(doNotDispose, DisposalMethod.DO_NOT_DISPOSE);
        assertEquals(restoreToBackground, DisposalMethod.RESTORE_TO_BACKGROUND);
        assertEquals(restoreToPrevious, DisposalMethod.RESTORE_TO_PREVIOUS);
        assertEquals(toBeDefined1, DisposalMethod.TO_BE_DEFINED_1);
        assertEquals(toBeDefined2, DisposalMethod.TO_BE_DEFINED_2);
        assertEquals(toBeDefined3, DisposalMethod.TO_BE_DEFINED_3);
        assertEquals(toBeDefined4, DisposalMethod.TO_BE_DEFINED_4);
    }

    @Test
    void testCreateMetadataWithDisposalMethods() {
        for (final DisposalMethod disposalMethod : DisposalMethod.values()) {
            final GifImageMetadataItem metadataItem = new GifImageMetadataItem(0, 0, 0, false, -1, disposalMethod);
            assertEquals(disposalMethod, metadataItem.getDisposalMethod());
        }
    }

    @ParameterizedTest
    @MethodSource("data")
    void testImageDimensions(final File imageFile) throws Exception {
        final ImageInfo imageInfo = Imaging.getImageInfo(imageFile);
        final GifImageMetadata metadata = (GifImageMetadata) Imaging.getMetadata(imageFile);
        final List<BufferedImage> images = Imaging.getAllBufferedImages(imageFile);

        if (images.size() == 1) {
            final BufferedImage image = images.get(0);
            final GifImageMetadataItem metadataItem = metadata.getItems().get(0);
            assertEquals(metadata.getWidth(), image.getWidth() + metadataItem.getLeftPosition());
            assertEquals(metadata.getHeight(), image.getHeight() + metadataItem.getTopPosition());
        } else {
            for (final BufferedImage image : images) {
                assertEquals(metadata.getWidth(), image.getWidth());
                assertEquals(metadata.getHeight(), image.getHeight());
            }
        }

        assertEquals(metadata.getWidth(), imageInfo.getWidth());
        assertEquals(metadata.getHeight(), imageInfo.getHeight());
    }

    @ParameterizedTest
    @MethodSource("data")
    void testImageInfo(final File imageFile) throws Exception {
        final ImageInfo imageInfo = Imaging.getImageInfo(imageFile);
        assertNotNull(imageInfo);
    }

    @ParameterizedTest
    @MethodSource("data")
    void testMetadata(final File imageFile) throws IOException {
        final ImageMetadata metadata = Imaging.getMetadata(imageFile);
        assertNotNull(metadata);
        assertInstanceOf(GifImageMetadata.class, metadata);
        assertTrue(((GifImageMetadata) metadata).getWidth() > 0);
        assertTrue(((GifImageMetadata) metadata).getHeight() > 0);
        assertNotNull(metadata.getItems());
    }

    private List<ImageIoGifFrameMetadata> getImageIoGifFrameMetadata(final File imageFile) throws IOException, ImagingException {
        final Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        assertTrue(readers.hasNext());

        try (ImageInputStream imageInputStream = ImageIO.createImageInputStream(imageFile)) {
            final ImageReader reader = readers.next();
            try {
                reader.setInput(imageInputStream, false, false);
                final int frameCount = reader.getNumImages(true);
                final List<ImageIoGifFrameMetadata> metadata = new ArrayList<>(frameCount);
                for (int i = 0; i < frameCount; i++) {
                    metadata.add(readImageIoFrameMetadata(reader, i));
                }
                return metadata;
            } finally {
                reader.dispose();
            }
        }
    }

    private IIOMetadataNode getRequiredNode(final IIOMetadataNode root, final String nodeName) throws ImagingException {
        final NodeList nodes = root.getElementsByTagName(nodeName);
        if (nodes.getLength() == 0) {
            throw new ImagingException("Missing GIF metadata node: " + nodeName);
        }
        return (IIOMetadataNode) nodes.item(0);
    }

    private boolean imagesDiffer(final BufferedImage firstImage, final BufferedImage secondImage) {
        if (firstImage.getWidth() != secondImage.getWidth() || firstImage.getHeight() != secondImage.getHeight()) {
            return true;
        }
        for (int y = 0; y < firstImage.getHeight(); y++) {
            for (int x = 0; x < firstImage.getWidth(); x++) {
                if (firstImage.getRGB(x, y) != secondImage.getRGB(x, y)) {
                    return true;
                }
            }
        }
        return false;
    }

    private int parseIntAttribute(final IIOMetadataNode node, final String attributeName) {
        return Integer.parseInt(node.getAttribute(attributeName));
    }

    private ImageIoGifFrameMetadata readImageIoFrameMetadata(final ImageReader reader, final int imageIndex) throws IOException, ImagingException {
        final IIOMetadata metadata = reader.getImageMetadata(imageIndex);
        final IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metadata.getNativeMetadataFormatName());
        final IIOMetadataNode graphicControlExtension = getRequiredNode(root, "GraphicControlExtension");
        final IIOMetadataNode imageDescriptor = getRequiredNode(root, "ImageDescriptor");

        final int delay = parseIntAttribute(graphicControlExtension, "delayTime");
        final int leftPosition = parseIntAttribute(imageDescriptor, "imageLeftPosition");
        final int topPosition = parseIntAttribute(imageDescriptor, "imageTopPosition");
        final boolean transparent = Boolean.parseBoolean(graphicControlExtension.getAttribute("transparentColorFlag"));
        final int transparentColorIndex = transparent ? parseIntAttribute(graphicControlExtension, "transparentColorIndex") : -1;
        final DisposalMethod disposalMethod = toDisposalMethod(graphicControlExtension.getAttribute("disposalMethod"));

        return new ImageIoGifFrameMetadata(delay, leftPosition, topPosition, transparent, transparentColorIndex, disposalMethod);
    }

    private DisposalMethod toDisposalMethod(final String disposalMethod) throws ImagingException {
        switch (disposalMethod) {
        case "none":
            return DisposalMethod.UNSPECIFIED;
        case "doNotDispose":
            return DisposalMethod.DO_NOT_DISPOSE;
        case "restoreToBackgroundColor":
            return DisposalMethod.RESTORE_TO_BACKGROUND;
        case "restoreToPrevious":
            return DisposalMethod.RESTORE_TO_PREVIOUS;
        default:
            if (disposalMethod.startsWith("undefinedDisposalMethod")) {
                return GifImageParser.createDisposalMethodFromIntValue(Integer.parseInt(disposalMethod.substring("undefinedDisposalMethod".length())));
            }
            throw new ImagingException("Unexpected GIF disposal method: " + disposalMethod);
        }
    }

    /**
     * The GIF image Lzw compression may contain a table with length inferior to the length of entries in the image data. Which results in an
     * ArrayOutOfBoundsException. This verifies that instead of throwing an AOOBE, we are handling the case and informing the user why the parser failed to read
     * it, by throwin an ImageReadException with a more descriptive message.
     *
     * <p>
     * See Google OSS Fuzz issue 33464
     * </p>
     *
     * @throws IOException if it fails to read the test image
     */
    @Test
    void testUncaughtExceptionOssFuzz33464() throws IOException {
        final File file = TestResources.resourceToFile("/images/gif/oss-fuzz-33464/clusterfuzz-testcase-minimized-ImagingGifFuzzer-5174009164595200");
        final GifImageParser parser = new GifImageParser();
        assertThrows(ImagingException.class, () -> parser.getBufferedImage(ByteSource.file(file), new GifImagingParameters()));
    }

    /**
     * The GIF image data may lead to out of bound array access. This test verifies that we handle that case and raise an appropriate exception.
     *
     * <p>
     * See Google OSS Fuzz issue 33501
     * </p>
     *
     * @throws IOException if it fails to read the test image
     */
    @Test
    void testUncaughtExceptionOssFuzz33501() throws IOException {
        final File file = TestResources.resourceToFile("/images/gif/oss-fuzz-33501/clusterfuzz-testcase-minimized-ImagingGifFuzzer-5914278319226880");
        final GifImageParser parser = new GifImageParser();
        assertThrows(ImagingException.class, () -> parser.getBufferedImage(ByteSource.file(file), new GifImagingParameters()));
    }

    /**
     * Test that invalid indexes are validated when accessing GIF color table array.
     *
     * <p>
     * See Google OSS Fuzz issue 34185
     * </p>
     *
     * @throws IOException if it fails to read the test image
     */
    @Test
    void testUncaughtExceptionOssFuzz34185() throws IOException {
        final File file = TestResources.resourceToFile("/images/gif/IMAGING-318/clusterfuzz-testcase-minimized-ImagingGifFuzzer-5005192379629568");
        final GifImageParser parser = new GifImageParser();
        assertThrows(ImagingException.class, () -> parser.getBufferedImage(ByteSource.file(file), new GifImagingParameters()));
    }
}
