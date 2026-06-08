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

import org.apache.commons.imaging.common.XmpImagingParameters;

/**
 * WebP format parameters.
 *
 * @since 1.0.0-alpha4
 */
public class WebPImagingParameters extends XmpImagingParameters<WebPImagingParameters> {

    /**
     * Default compression level (6).
     */
    public static final int DEFAULT_COMPRESSION_LEVEL = 6;

    /**
     * Minimum compression level (1, fastest).
     */
    public static final int MIN_COMPRESSION_LEVEL = 1;

    /**
     * Maximum compression level (9, smallest file).
     */
    public static final int MAX_COMPRESSION_LEVEL = 9;

    /**
     * Compression level for WebP writing (1-9, default 6).
     */
    private int compressionLevel = DEFAULT_COMPRESSION_LEVEL;

    /**
     * EXIF data to embed in the WebP image.
     */
    private byte[] exifData;

    /**
     * Constructs a new instance.
     */
    public WebPImagingParameters() {
        // Default constructor
    }

    /**
     * Gets the compression level.
     *
     * @return the compression level.
     */
    public int getCompressionLevel() {
        return compressionLevel;
    }

    /**
     * Sets the compression level (1-9).
     *
     * @param compressionLevel the compression level to set.
     * @return this instance.
     */
    public WebPImagingParameters setCompressionLevel(final int compressionLevel) {
        if (compressionLevel < MIN_COMPRESSION_LEVEL || compressionLevel > MAX_COMPRESSION_LEVEL) {
            this.compressionLevel = DEFAULT_COMPRESSION_LEVEL;
        } else {
            this.compressionLevel = compressionLevel;
        }
        return asThis();
    }

    /**
     * Gets the EXIF data.
     *
     * @return the EXIF data.
     */
    public byte[] getExifData() {
        return exifData != null ? exifData.clone() : null;
    }

    /**
     * Sets the EXIF data.
     *
     * @param exifData the EXIF data to set.
     * @return this instance.
     */
    public WebPImagingParameters setExifData(final byte[] exifData) {
        this.exifData = exifData != null ? exifData.clone() : null;
        return asThis();
    }
}
