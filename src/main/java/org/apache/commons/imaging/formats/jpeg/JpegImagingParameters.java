/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */

package org.apache.commons.imaging.formats.jpeg;

import org.apache.commons.imaging.common.XmpImagingParameters;

/**
 * JPEG format parameters.
 *
 * @since 1.0-alpha3
 */
public class JpegImagingParameters extends XmpImagingParameters<JpegImagingParameters> {

    private int compressionQuality = 85;
    private byte[] exifData;
    private byte[] iccProfileData;

    /**
     * Constructs a new instance.
     */
    public JpegImagingParameters() {
    }

    /**
     * Gets the compression quality (1-100).
     *
     * @return the compression quality
     */
    public int getCompressionQuality() {
        return compressionQuality;
    }

    /**
     * Sets the compression quality (1-100).
     *
     * @param compressionQuality the compression quality
     * @return this instance
     */
    public JpegImagingParameters setCompressionQuality(final int compressionQuality) {
        this.compressionQuality = Math.max(1, Math.min(100, compressionQuality));
        return this;
    }

    /**
     * Gets the raw EXIF data to embed.
     *
     * @return the raw EXIF data or null
     */
    public byte[] getExifData() {
        return exifData != null ? exifData.clone() : null;
    }

    /**
     * Sets the raw EXIF data to embed.
     *
     * @param exifData the raw EXIF data
     * @return this instance
     */
    public JpegImagingParameters setExifData(final byte[] exifData) {
        this.exifData = exifData != null ? exifData.clone() : null;
        return this;
    }

    /**
     * Gets the raw ICC profile data to embed.
     *
     * @return the raw ICC profile data or null
     */
    public byte[] getIccProfileData() {
        return iccProfileData != null ? iccProfileData.clone() : null;
    }

    /**
     * Sets the raw ICC profile data to embed.
     *
     * @param iccProfileData the raw ICC profile data
     * @return this instance
     */
    public JpegImagingParameters setIccProfileData(final byte[] iccProfileData) {
        this.iccProfileData = iccProfileData != null ? iccProfileData.clone() : null;
        return this;
    }
}
