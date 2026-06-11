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

import java.io.IOException;
import java.util.List;

import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.ImagingFormatException;
import org.apache.commons.imaging.bytesource.ByteSource;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.common.ImageMetadata.ImageMetadataItem;
import org.apache.commons.imaging.formats.tiff.JpegImageData;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.TiffImagingParameters;

/**
 * Parser for extracting EXIF thumbnail data from JPEG byte arrays.
 */
public final class JpegThumbnailParser {

    private static final int SOI_MARKER = 0xFFD8;
    private static final int EOI_MARKER = 0xFFD9;

    private JpegThumbnailParser() {
    }

    /**
     * Extracts thumbnail data from a JPEG byte array.
     *
     * @param jpegBytes the JPEG byte array.
     * @return the thumbnail data bytes.
     * @throws ImagingFormatException if the thumbnail data is corrupted or unreadable.
     */
    public static byte[] extractThumbnail(final byte[] jpegBytes) {
        if (jpegBytes == null || jpegBytes.length == 0) {
            throw new ImagingFormatException("Thumbnail extraction failed: input is null or empty");
        }

        final TiffImageMetadata exif;
        try {
            final ByteSource byteSource = ByteSource.array(jpegBytes);
            final JpegImageParser parser = new JpegImageParser();
            final ImageMetadata metadata = parser.getMetadata(byteSource, new JpegImagingParameters());
            if (!(metadata instanceof JpegImageMetadata)) {
                throw new ImagingFormatException("Thumbnail extraction failed: no JPEG metadata found");
            }
            final JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
            exif = jpegMetadata.getExif();
            if (exif == null) {
                throw new ImagingFormatException("Thumbnail extraction failed: no EXIF metadata found");
            }
        } catch (final ImagingFormatException e) {
            throw e;
        } catch (final ImagingException | IOException e) {
            throw new ImagingFormatException("Thumbnail extraction failed: " + e.getMessage(), e);
        } catch (final RuntimeException e) {
            throw new ImagingFormatException("Thumbnail extraction failed: " + e.getMessage(), e);
        }

        validateTiffStructure(exif);

        final byte[] data = getThumbnailData(exif);
        if (data == null || data.length == 0) {
            throw new ImagingFormatException("Thumbnail extraction failed: no thumbnail data found");
        }

        validateThumbnailData(data);

        return data;
    }

    private static void validateTiffStructure(final TiffImageMetadata exif) {
        final List<? extends ImageMetadataItem> directories = exif.getDirectories();
        for (final ImageMetadataItem item : directories) {
            final TiffImageMetadata.Directory dir = (TiffImageMetadata.Directory) item;
            if (dir.getJpegImageData() != null) {
                final org.apache.commons.imaging.formats.tiff.TiffDirectory tiffDir = exif.findDirectory(dir.type);
                if (tiffDir != null && tiffDir.getNextDirectoryOffset() == 0xFFFFFFFFL) {
                    throw new ImagingFormatException("Thumbnail TIFF structure corrupted: invalid Next IFD offset 0xFFFFFFFF");
                }
            }
        }
    }

    private static byte[] getThumbnailData(final TiffImageMetadata exif) {
        final List<? extends ImageMetadataItem> directories = exif.getDirectories();
        for (final ImageMetadataItem item : directories) {
            final TiffImageMetadata.Directory dir = (TiffImageMetadata.Directory) item;
            final JpegImageData jpegImageData = dir.getJpegImageData();
            if (jpegImageData != null) {
                return jpegImageData.getData();
            }
        }
        return null;
    }

    private static void validateThumbnailData(final byte[] data) {
        if (data.length < 4) {
            throw new ImagingFormatException("Thumbnail data too short: " + data.length + " bytes");
        }
        final int soi = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        if (soi != SOI_MARKER) {
            throw new ImagingFormatException("Thumbnail data missing SOI marker");
        }
        final int eoi = ((data[data.length - 2] & 0xFF) << 8) | (data[data.length - 1] & 0xFF);
        if (eoi != EOI_MARKER) {
            throw new ImagingFormatException("Thumbnail data truncated or missing EOI marker");
        }
    }
}