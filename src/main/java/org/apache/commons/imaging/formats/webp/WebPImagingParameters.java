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

import java.util.Objects;

import org.apache.commons.imaging.common.XmpImagingParameters;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;

/**
 * WebP format parameters.
 *
 * @since 1.0.0-alpha4
 */
public class WebPImagingParameters extends XmpImagingParameters<WebPImagingParameters> {

    /**
     * Default compression level.
     */
    public static final int DEFAULT_COMPRESSION_LEVEL = 6;

    /**
     * Minimum compression level.
     */
    public static final int MIN_COMPRESSION_LEVEL = 1;

    /**
     * Maximum compression level.
     */
    public static final int MAX_COMPRESSION_LEVEL = 9;

    private int compressionLevel = DEFAULT_COMPRESSION_LEVEL;

    private byte[] exifBytes;

    private TiffOutputSet tiffOutputSet;

    /**
     * Constructs a new instance.
     */
    public WebPImagingParameters() {
        // Default constructor
    }

    /**
     * Gets the compression level.
     *
     * @return the compression level (1-9).
     */
    public int getCompressionLevel() {
        return compressionLevel;
    }

    /**
     * Sets the compression level.
     *
     * @param compressionLevel the compression level (1-9, where 1 is fastest and 9 is best compression).
     * @return this instance for method chaining.
     */
    public WebPImagingParameters setCompressionLevel(final int compressionLevel) {
        if (compressionLevel < MIN_COMPRESSION_LEVEL || compressionLevel > MAX_COMPRESSION_LEVEL) {
            throw new IllegalArgumentException("Compression level must be between " + MIN_COMPRESSION_LEVEL + " and " + MAX_COMPRESSION_LEVEL);
        }
        this.compressionLevel = compressionLevel;
        return this;
    }

    /**
     * Gets the EXIF bytes.
     *
     * @return the EXIF bytes, or null if not set.
     */
    public byte[] getExifBytes() {
        return exifBytes;
    }

    /**
     * Sets the EXIF bytes.
     *
     * @param exifBytes the EXIF bytes.
     * @return this instance for method chaining.
     */
    public WebPImagingParameters setExifBytes(final byte[] exifBytes) {
        this.exifBytes = exifBytes != null ? exifBytes.clone() : null;
        return this;
    }

    /**
     * Gets the TIFF output set for writing EXIF metadata.
     *
     * @return the TIFF output set, or null if not set.
     */
    public TiffOutputSet getTiffOutputSet() {
        return tiffOutputSet;
    }

    /**
     * Sets the TIFF output set for writing EXIF metadata.
     *
     * @param tiffOutputSet the TIFF output set.
     * @return this instance for method chaining.
     */
    public WebPImagingParameters setTiffOutputSet(final TiffOutputSet tiffOutputSet) {
        this.tiffOutputSet = tiffOutputSet;
        return this;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final WebPImagingParameters that = (WebPImagingParameters) o;
        return compressionLevel == that.compressionLevel && Objects.equals(tiffOutputSet, that.tiffOutputSet) && Objects.deepEquals(exifBytes, that.exifBytes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), compressionLevel, tiffOutputSet);
    }
}
