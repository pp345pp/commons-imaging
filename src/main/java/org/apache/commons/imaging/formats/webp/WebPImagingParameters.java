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

public class WebPImagingParameters extends XmpImagingParameters<WebPImagingParameters> {

    private static final int DEFAULT_COMPRESSION_LEVEL = 6;

    private int compressionLevel = DEFAULT_COMPRESSION_LEVEL;
    private TiffOutputSet outputSet;

    public WebPImagingParameters() {
    }

    public int getCompressionLevel() {
        return compressionLevel;
    }

    public TiffOutputSet getOutputSet() {
        return outputSet;
    }

    public WebPImagingParameters setCompressionLevel(final int compressionLevel) {
        if (compressionLevel < 1 || compressionLevel > 9) {
            throw new IllegalArgumentException("Compression level must be between 1 and 9");
        }
        this.compressionLevel = compressionLevel;
        return asThis();
    }

    public WebPImagingParameters setOutputSet(final TiffOutputSet outputSet) {
        this.outputSet = outputSet;
        return asThis();
    }
}
