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
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;

/**
 * JPEG format parameters.
 *
 * @since 1.0-alpha3
 */
public class JpegImagingParameters extends XmpImagingParameters<JpegImagingParameters> {

    private TiffOutputSet exif;
    private byte[] iccProfile;
    private int quality = 75; // 1-100

    /**
     * Constructs a new instance.
     */
    public JpegImagingParameters() {
    }

    public TiffOutputSet getExif() {
        return exif;
    }

    public void setExif(final TiffOutputSet exif) {
        this.exif = exif;
    }

    public byte[] getIccProfile() {
        return iccProfile;
    }

    public void setIccProfile(final byte[] iccProfile) {
        this.iccProfile = iccProfile;
    }

    public int getQuality() {
        return quality;
    }

    public void setQuality(final int quality) {
        if (quality < 1 || quality > 100) {
            throw new IllegalArgumentException("Quality must be between 1 and 100");
        }
        this.quality = quality;
    }
}
