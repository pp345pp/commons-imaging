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

import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGHuffmanTable;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.plugins.jpeg.JPEGQTable;
import javax.imageio.stream.ImageOutputStream;

import org.apache.commons.imaging.AbstractImageWriter;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.apache.commons.imaging.formats.jpeg.xmp.JpegRewriter;

public class JpegImageWriter extends JpegRewriter implements AbstractImageWriter<JpegImagingParameters> {

    private static final byte[] EXIF_PREFIX = { 0x45, 0x78, 0x69, 0x66, 0x00, 0x00 };
    private static final byte[] ICC_PROFILE_LABEL = { 0x49, 0x43, 0x43, 0x5F, 0x50, 0x52, 0x4F, 0x46, 0x49, 0x4C, 0x45, 0x00 };
    private static final int MAX_SEGMENT_DATA_SIZE = JpegConstants.MAX_SEGMENT_SIZE - 2;
    private static final int MAX_ICC_CHUNK_SIZE = MAX_SEGMENT_DATA_SIZE - ICC_PROFILE_LABEL.length - 2;

    @Override
    public void writeImage(final BufferedImage src, final OutputStream os, JpegImagingParameters params) throws ImagingException, IOException {
        Objects.requireNonNull(src, "src");
        Objects.requireNonNull(os, "os");

        if (params == null) {
            params = new JpegImagingParameters();
        }

        final int quality = params.getQuality();
        if (quality < 1 || quality > 100) {
            throw new ImagingException("JPEG quality must be between 1 and 100: " + quality);
        }

        final BufferedImage image = normalizeImage(src);
        final boolean grayscale = image.getRaster().getNumBands() == 1;
        final byte[] encodedImage = encodeImage(image, quality, grayscale);
        final byte[] exif = resolveExifBytes(params);
        final byte[] iccProfile = resolveIccProfileBytes(params);

        if (exif == null && iccProfile == null) {
            os.write(encodedImage);
            return;
        }

        final byte[] imageWithMetadata = rewriteMetadata(encodedImage, exif, iccProfile);
        os.write(imageWithMetadata);
    }

    private List<JFIFPieceSegment> buildIccSegments(final byte[] iccProfile) throws ImagingException {
        final List<JFIFPieceSegment> result = new ArrayList<>();
        if (iccProfile == null || iccProfile.length == 0) {
            return result;
        }

        final int markerCount = (iccProfile.length + MAX_ICC_CHUNK_SIZE - 1) / MAX_ICC_CHUNK_SIZE;
        if (markerCount > 255) {
            throw new ImagingException("ICC profile is too large for JPEG APP2 segments: " + iccProfile.length);
        }

        for (int markerIndex = 0, offset = 0; markerIndex < markerCount; markerIndex++, offset += MAX_ICC_CHUNK_SIZE) {
            final int chunkLength = Math.min(MAX_ICC_CHUNK_SIZE, iccProfile.length - offset);
            final byte[] segmentData = new byte[ICC_PROFILE_LABEL.length + 2 + chunkLength];
            System.arraycopy(ICC_PROFILE_LABEL, 0, segmentData, 0, ICC_PROFILE_LABEL.length);
            segmentData[ICC_PROFILE_LABEL.length] = (byte) (markerIndex + 1);
            segmentData[ICC_PROFILE_LABEL.length + 1] = (byte) markerCount;
            System.arraycopy(iccProfile, offset, segmentData, ICC_PROFILE_LABEL.length + 2, chunkLength);
            result.add(new JFIFPieceSegment(JpegConstants.JPEG_APP2_MARKER, segmentData));
        }
        return result;
    }

    private BufferedImage convertToType(final BufferedImage src, final int bufferedImageType) {
        if (src.getType() == bufferedImageType) {
            return src;
        }
        final BufferedImage converted = new BufferedImage(src.getWidth(), src.getHeight(), bufferedImageType);
        final Graphics2D graphics = converted.createGraphics();
        try {
            graphics.drawImage(src, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return converted;
    }

    private byte[] createExifSegmentData(final byte[] exif) throws ImagingException {
        if (exif == null || exif.length == 0) {
            return null;
        }
        final byte[] segmentData = new byte[EXIF_PREFIX.length + exif.length];
        if (segmentData.length > MAX_SEGMENT_DATA_SIZE) {
            throw new ImagingException("EXIF metadata is too large for a JPEG APP1 segment: " + exif.length);
        }
        System.arraycopy(EXIF_PREFIX, 0, segmentData, 0, EXIF_PREFIX.length);
        System.arraycopy(exif, 0, segmentData, EXIF_PREFIX.length, exif.length);
        return segmentData;
    }

    private JPEGQTable createQuantizationTable(final JPEGQTable baseTable, final int quality) {
        final int scalingFactor = quality < 50 ? 5000 / quality : 200 - quality * 2;
        final int[] scaledTable = new int[baseTable.getTable().length];
        for (int i = 0; i < scaledTable.length; i++) {
            final int scaledValue = (baseTable.getTable()[i] * scalingFactor + 50) / 100;
            scaledTable[i] = Math.max(1, Math.min(255, scaledValue));
        }
        return new JPEGQTable(scaledTable);
    }

    private byte[] encodeImage(final BufferedImage image, final int quality, final boolean grayscale) throws ImagingException, IOException {
        final Iterator<ImageWriter> imageWriters = ImageIO.getImageWritersByFormatName("jpeg");
        if (!imageWriters.hasNext()) {
            throw new ImagingException("No JPEG ImageIO writer is available.");
        }

        final ImageWriter imageWriter = imageWriters.next();
        final JPEGImageWriteParam writeParam = new JPEGImageWriteParam(null);
        writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        writeParam.setCompressionQuality(quality / 100f);
        writeParam.setOptimizeHuffmanTables(false);
        writeParam.setProgressiveMode(ImageWriteParam.MODE_DISABLED);

        if (grayscale) {
            writeParam.setEncodeTables(new JPEGQTable[] { createQuantizationTable(JPEGQTable.K1Luminance, quality) },
                    new JPEGHuffmanTable[] { JPEGHuffmanTable.StdDCLuminance }, new JPEGHuffmanTable[] { JPEGHuffmanTable.StdACLuminance });
        } else {
            writeParam.setEncodeTables(
                    new JPEGQTable[] { createQuantizationTable(JPEGQTable.K1Luminance, quality), createQuantizationTable(JPEGQTable.K2Chrominance, quality) },
                    new JPEGHuffmanTable[] { JPEGHuffmanTable.StdDCLuminance, JPEGHuffmanTable.StdDCChrominance },
                    new JPEGHuffmanTable[] { JPEGHuffmanTable.StdACLuminance, JPEGHuffmanTable.StdACChrominance });
        }

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(byteArrayOutputStream)) {
            imageWriter.setOutput(imageOutputStream);
            imageWriter.write(null, new IIOImage(image, null, null), writeParam);
            imageOutputStream.flush();
        } finally {
            imageWriter.dispose();
        }
        return byteArrayOutputStream.toByteArray();
    }

    private boolean isIccProfileSegment(final JFIFPieceSegment segment) {
        return segment.marker == JpegConstants.JPEG_APP2_MARKER && JpegConstants.ICC_PROFILE_LABEL.isStartOf(segment.getSegmentData());
    }

    private BufferedImage normalizeImage(final BufferedImage src) throws ImagingException {
        if (src.getColorModel().hasAlpha()) {
            throw new ImagingException("JPEG writer only supports opaque 8-bit RGB or grayscale images.");
        }

        for (final int sampleSize : src.getSampleModel().getSampleSize()) {
            if (sampleSize != 8) {
                throw new ImagingException("JPEG writer only supports 8-bit samples.");
            }
        }

        if (src.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_GRAY || src.getRaster().getNumBands() == 1) {
            return convertToType(src, BufferedImage.TYPE_BYTE_GRAY);
        }

        if (src.getColorModel().getNumColorComponents() == 3) {
            return convertToType(src, BufferedImage.TYPE_INT_RGB);
        }

        throw new ImagingException("JPEG writer only supports RGB or grayscale images.");
    }

    private byte[] resolveExifBytes(final JpegImagingParameters params) throws ImagingException, IOException {
        if (params.getExif() != null) {
            return params.getExif();
        }
        final ByteSource metadataSource = params.getMetadataSource();
        if (metadataSource == null) {
            return null;
        }
        return new JpegImageParser().getExifRawData(metadataSource);
    }

    private byte[] resolveIccProfileBytes(final JpegImagingParameters params) throws ImagingException, IOException {
        if (params.getIccProfile() != null) {
            return params.getIccProfile();
        }
        final ByteSource metadataSource = params.getMetadataSource();
        if (metadataSource == null) {
            return null;
        }
        return new JpegImageParser().getIccProfileBytes(metadataSource, null);
    }

    private byte[] rewriteMetadata(final byte[] encodedImage, final byte[] exif, final byte[] iccProfile) throws ImagingException, IOException {
        final JFIFPieces jfifPieces = analyzeJfif(ByteSource.array(encodedImage));
        final List<JFIFPiece> cleanedPieces = new ArrayList<>(jfifPieces.pieces.size());
        for (final JFIFPiece piece : jfifPieces.pieces) {
            if (piece instanceof JFIFPieceSegment) {
                final JFIFPieceSegment segment = (JFIFPieceSegment) piece;
                if (segment.isExifSegment() || isIccProfileSegment(segment)) {
                    continue;
                }
            }
            cleanedPieces.add(piece);
        }

        final List<JFIFPieceSegment> metadataSegments = new ArrayList<>();
        final byte[] exifSegmentData = createExifSegmentData(exif);
        if (exifSegmentData != null) {
            metadataSegments.add(new JFIFPieceSegment(JpegConstants.JPEG_APP1_MARKER, exifSegmentData));
        }
        metadataSegments.addAll(buildIccSegments(iccProfile));

        if (metadataSegments.isEmpty()) {
            return encodedImage;
        }

        final List<JFIFPiece> piecesWithMetadata = insertAfterLastAppSegments(cleanedPieces, metadataSegments);
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(encodedImage.length + 4096);
        writeSegments(byteArrayOutputStream, piecesWithMetadata);
        return byteArrayOutputStream.toByteArray();
    }
}
