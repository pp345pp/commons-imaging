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

    public static final int DEFAULT_QUALITY = 75;

    private int quality = DEFAULT_QUALITY;
    private byte[] exifData;
    private byte[] iccProfile;

    public JpegImagingParameters() {
    }

    public int getQuality() {
        return quality;
    }

    public JpegImagingParameters setQuality(final int quality) {
        if (quality < 1 || quality > 100) {
            throw new IllegalArgumentException("Quality must be between 1 and 100, got: " + quality);
        }
        this.quality = quality;
        return asThis();
    }

    public byte[] getExifData() {
        return exifData;
    }

    public JpegImagingParameters setExifData(final byte[] exifData) {
        this.exifData = exifData;
        return asThis();
    }

    public byte[] getIccProfile() {
        return iccProfile;
    }

    public JpegImagingParameters setIccProfile(final byte[] iccProfile) {
        this.iccProfile = iccProfile;
        return asThis();
    }
}
