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

import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;

/**
 * WebP format parameters.
 *
 * @since 1.0.0-alpha4
 */
public class WebPImagingParameters extends XmpImagingParameters<WebPImagingParameters> {

    private int compressionLevel = 9;
    private TiffOutputSet exif;

    /**
     * Constructs a new instance.
     */
    public WebPImagingParameters() {
        // Default constructor
    }

    /**
     * Gets the EXIF metadata.
     *
     * @return the EXIF metadata.
     */
    public TiffOutputSet getExif() {
        return exif;
    }

    /**
     * Sets the EXIF metadata.
     *
     * @param exif the EXIF metadata.
     */
    public void setExif(TiffOutputSet exif) {
        this.exif = exif;
    }

    /**
     * Gets the compression level (1-9).
     *
     * @return the compression level.
     */
    public int getCompressionLevel() {
        return compressionLevel;
    }

    /**
     * Sets the compression level (1-9).
     *
     * @param compressionLevel the compression level (1-9).
     */
    public void setCompressionLevel(int compressionLevel) {
        if (compressionLevel < 1 || compressionLevel > 9) {
            throw new IllegalArgumentException("Compression level must be between 1 and 9");
        }
        this.compressionLevel = compressionLevel;
    }
}
