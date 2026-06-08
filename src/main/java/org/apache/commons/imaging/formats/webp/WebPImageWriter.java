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

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.common.AbstractBinaryOutputStream;
import org.apache.commons.imaging.formats.tiff.write.TiffImageWriterLossy;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;

public class WebPImageWriter {

    private static final int VP8X_FLAG_ALPHA = 0x10;
    private static final int VP8X_FLAG_EXIF = 0x08;
    private static final int VP8X_FLAG_XMP = 0x04;

    public void writeImage(final BufferedImage src, final OutputStream os, final WebPImagingParameters params) throws ImagingException, IOException {
        Objects.requireNonNull(src, "src");
        Objects.requireNonNull(os, "os");

        final WebPImagingParameters actualParams = params == null ? new WebPImagingParameters() : params;
        final int width = src.getWidth();
        final int height = src.getHeight();
        final boolean hasAlpha = WebPLosslessCodec.hasTransparency(src);
        final byte[] vp8lData = WebPLosslessCodec.encode(src, actualParams.getCompressionLevel());
        final byte[] exifBytes = toExifBytes(actualParams.getOutputSet());
        final byte[] xmpBytes = toXmpBytes(actualParams.getXmpXml());
        final int vp8xFlags = (hasAlpha ? VP8X_FLAG_ALPHA : 0) | (exifBytes != null ? VP8X_FLAG_EXIF : 0) | (xmpBytes != null ? VP8X_FLAG_XMP : 0);

        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeChunk(body, "VP8X", createVp8xChunk(vp8xFlags, width, height));
        writeChunk(body, "VP8L", vp8lData);
        if (exifBytes != null) {
            writeChunk(body, "EXIF", exifBytes);
        }
        if (xmpBytes != null) {
            writeChunk(body, "XMP ", xmpBytes);
        }

        WebPConstants.RIFF_SIGNATURE.writeTo(os);
        final AbstractBinaryOutputStream binaryOutputStream = AbstractBinaryOutputStream.littleEndian(os);
        binaryOutputStream.write4Bytes(body.size() + 4);
        WebPConstants.WEBP_SIGNATURE.writeTo(os);
        os.write(body.toByteArray());
    }

    private byte[] createVp8xChunk(final int flags, final int width, final int height) {
        final byte[] chunk = new byte[10];
        chunk[0] = (byte) flags;

        final int widthMinusOne = width - 1;
        final int heightMinusOne = height - 1;
        chunk[4] = (byte) widthMinusOne;
        chunk[5] = (byte) (widthMinusOne >> 8);
        chunk[6] = (byte) (widthMinusOne >> 16);
        chunk[7] = (byte) heightMinusOne;
        chunk[8] = (byte) (heightMinusOne >> 8);
        chunk[9] = (byte) (heightMinusOne >> 16);
        return chunk;
    }

    private byte[] toExifBytes(final TiffOutputSet outputSet) throws ImagingException, IOException {
        if (outputSet == null) {
            return null;
        }
        final ByteArrayOutputStream exifOutput = new ByteArrayOutputStream();
        new TiffImageWriterLossy(outputSet.byteOrder).write(exifOutput, outputSet);
        return exifOutput.toByteArray();
    }

    private byte[] toXmpBytes(final String xmpXml) {
        return xmpXml == null ? null : xmpXml.getBytes(StandardCharsets.UTF_8);
    }

    private void writeChunk(final OutputStream os, final String fourCc, final byte[] payload) throws IOException {
        os.write(fourCc.getBytes(StandardCharsets.US_ASCII));
        final AbstractBinaryOutputStream binaryOutputStream = AbstractBinaryOutputStream.littleEndian(os);
        binaryOutputStream.write4Bytes(payload.length);
        os.write(payload);
        if ((payload.length & 1) != 0) {
            os.write(0);
        }
    }
}
