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

import java.io.File;

import org.apache.commons.imaging.bytesource.ByteSource;
import org.apache.commons.imaging.common.XmpImagingParameters;

/**
 * JPEG format parameters.
 *
 * @since 1.0-alpha3
 */
public class JpegImagingParameters extends XmpImagingParameters<JpegImagingParameters> {

    private int quality = 75;
    private byte[] exif;
    private byte[] iccProfile;
    private ByteSource metadataSource;

    /**
     * Constructs a new instance.
     */
    public JpegImagingParameters() {
    }

    public byte[] getExif() {
        return exif == null ? null : exif.clone();
    }

    public byte[] getIccProfile() {
        return iccProfile == null ? null : iccProfile.clone();
    }

    public ByteSource getMetadataSource() {
        return metadataSource;
    }

    public int getQuality() {
        return quality;
    }

    public JpegImagingParameters setExif(final byte[] exif) {
        this.exif = exif == null ? null : exif.clone();
        return asThis();
    }

    public JpegImagingParameters setIccProfile(final byte[] iccProfile) {
        this.iccProfile = iccProfile == null ? null : iccProfile.clone();
        return asThis();
    }

    public JpegImagingParameters setMetadataSource(final ByteSource metadataSource) {
        this.metadataSource = metadataSource;
        return asThis();
    }

    public JpegImagingParameters setMetadataSource(final byte[] metadataSource) {
        return setMetadataSource(metadataSource == null ? null : ByteSource.array(metadataSource));
    }

    public JpegImagingParameters setMetadataSource(final File metadataSource) {
        return setMetadataSource(metadataSource == null ? null : ByteSource.file(metadataSource));
    }

    public JpegImagingParameters setQuality(final int quality) {
        this.quality = quality;
        return asThis();
    }
}
