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

    /** Default JPEG quality: {@value}. */
    public static final float DEFAULT_QUALITY = 0.75f;

    private float quality = DEFAULT_QUALITY;

    /**
     * Constructs a new instance.
     */
    public JpegImagingParameters() {
    }

    /**
     * Sets the JPEG compression quality.
     *
     * @param quality the quality value, ranging from 0.0 (worst) to 1.0 (best)
     * @return this instance
     */
    public JpegImagingParameters setQuality(final float quality) {
        if (quality < 0.0f || quality > 1.0f) {
            throw new IllegalArgumentException("Quality must be between 0.0 and 1.0");
        }
        this.quality = quality;
        return this;
    }

    /**
     * Gets the JPEG compression quality.
     *
     * @return the quality value
     */
    public float getQuality() {
        return quality;
    }
}
