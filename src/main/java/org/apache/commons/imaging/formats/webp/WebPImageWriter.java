package org.apache.commons.imaging.formats.webp;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.common.LittleEndianBinaryOutputStream;
import org.apache.commons.imaging.formats.tiff.write.TiffImageWriterLossy;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;

/**
 * WebP image writer.
 *
 * @since 1.0.0-alpha4
 */
public class WebPImageWriter {

    private static class BitWriter {
        private final OutputStream os;
        private long bitBuffer;
        private int bitCount;

        BitWriter(final OutputStream os) {
            this.os = os;
        }

        void writeBits(final int value, final int bits) throws IOException {
            bitBuffer |= (value & ((1L << bits) - 1)) << bitCount;
            bitCount += bits;
            while (bitCount >= 8) {
                os.write((int) (bitBuffer & 0xFF));
                bitBuffer >>>= 8;
                bitCount -= 8;
            }
        }

        void flush() throws IOException {
            if (bitCount > 0) {
                os.write((int) (bitBuffer & 0xFF));
                bitBuffer = 0;
                bitCount = 0;
            }
        }
    }

    public void writeImage(final BufferedImage src, final OutputStream os, final WebPImagingParameters params)
            throws ImagingException, IOException {
        final int width = src.getWidth();
        final int height = src.getHeight();

        final boolean hasAlpha = src.getColorModel().hasAlpha();

        byte[] exifBytes = null;
        byte[] xmpBytes = null;
        
        if (params != null) {
            final TiffOutputSet exif = params.getExif();
            if (exif != null) {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                new TiffImageWriterLossy(ByteOrder.LITTLE_ENDIAN).write(baos, exif);
                exifBytes = baos.toByteArray();
            }
            final String xmpXml = params.getXmpXml();
            if (xmpXml != null) {
                xmpBytes = xmpXml.getBytes(StandardCharsets.UTF_8);
            }
        }

        final boolean useExtendedFormat = (exifBytes != null) || (xmpBytes != null) || hasAlpha;

        final LittleEndianBinaryOutputStream leos = new LittleEndianBinaryOutputStream(os);
        leos.write(WebPConstants.RIFF_SIGNATURE);
        
        // Calculate file size
        // WEBP signature: 4 bytes
        int fileSize = 4;
        
        if (useExtendedFormat) {
            fileSize += 8 + 10; // VP8X chunk header + payload
        }
        
        // VP8L chunk
        // payload size = 5 (header) + image bits
        // We need to calculate exactly how many bytes the image bits will take.
        // Let's generate the VP8L payload first to memory!
        final ByteArrayOutputStream vp8lPayload = new ByteArrayOutputStream();
        writeVp8lPayload(src, vp8lPayload, hasAlpha);
        final byte[] vp8lBytes = vp8lPayload.toByteArray();
        
        fileSize += 8 + vp8lBytes.length + (vp8lBytes.length % 2);
        
        if (exifBytes != null) {
            fileSize += 8 + exifBytes.length + (exifBytes.length % 2);
        }
        if (xmpBytes != null) {
            fileSize += 8 + xmpBytes.length + (xmpBytes.length % 2);
        }
        
        leos.writeInt(fileSize);
        leos.write(WebPConstants.WEBP_SIGNATURE);
        
        if (useExtendedFormat) {
            leos.writeInt(WebPChunkType.VP8X.value);
            leos.writeInt(10);
            
            int flags = 0;
            if (hasAlpha) flags |= 0x10;
            if (exifBytes != null) flags |= 0x08;
            if (xmpBytes != null) flags |= 0x04;
            
            leos.write(flags);
            leos.write(new byte[3]); // reserved
            leos.write(new byte[]{ (byte)(width - 1), (byte)((width - 1) >> 8), (byte)((width - 1) >> 16) });
            leos.write(new byte[]{ (byte)(height - 1), (byte)((height - 1) >> 8), (byte)((height - 1) >> 16) });
        }
        
        leos.writeInt(WebPChunkType.VP8L.value);
        leos.writeInt(vp8lBytes.length);
        leos.write(vp8lBytes);
        if (vp8lBytes.length % 2 != 0) leos.write(0);
        
        if (exifBytes != null) {
            leos.writeInt(WebPChunkType.EXIF.value);
            leos.writeInt(exifBytes.length);
            leos.write(exifBytes);
            if (exifBytes.length % 2 != 0) leos.write(0);
        }
        
        if (xmpBytes != null) {
            leos.writeInt(WebPChunkType.XMP.value);
            leos.writeInt(xmpBytes.length);
            leos.write(xmpBytes);
            if (xmpBytes.length % 2 != 0) leos.write(0);
        }
    }
    
    private void writeVp8lPayload(final BufferedImage src, final OutputStream os, final boolean hasAlpha) throws IOException {
        final BitWriter writer = new BitWriter(os);
        final int width = src.getWidth();
        final int height = src.getHeight();
        
        writer.writeBits(0x2F, 8);
        writer.writeBits(width - 1, 14);
        writer.writeBits(height - 1, 14);
        writer.writeBits(hasAlpha ? 1 : 0, 1);
        writer.writeBits(0, 3); // version
        
        writer.writeBits(0, 1); // no transforms
        writer.writeBits(0, 1); // no color cache
        writer.writeBits(0, 1); // no meta huffman
        
        // Green
        writeHuffman(writer, 280);
        // Red
        writeHuffman(writer, 256);
        // Blue
        writeHuffman(writer, 256);
        // Alpha
        writeHuffman(writer, 256);
        // Distance
        writer.writeBits(1, 1); // Simple
        writer.writeBits(0, 1); // 1 symbol
        writer.writeBits(0, 1); // is_first_8bits = 0
        writer.writeBits(0, 1); // symbol = 0
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                
                writer.writeBits(g, 8);
                writer.writeBits(r, 8);
                writer.writeBits(b, 8);
                writer.writeBits(a, 8);
            }
        }
        
        writer.flush();
    }
    
    private void writeHuffman(final BitWriter writer, final int numSymbols) throws IOException {
        writer.writeBits(0, 1); // Normal
        writer.writeBits(8, 4); // num_code_lengths - 4 = 12 - 4 = 8
        // lengths for 17, 18, 0, 1, 2, 3, 4, 5, 16, 6, 7, 8
        // index 2 is sym 0, index 11 is sym 8
        for (int i = 0; i < 12; i++) {
            if (i == 2 || i == 11) {
                writer.writeBits(1, 3);
            } else {
                writer.writeBits(0, 3);
            }
        }
        for (int i = 0; i < numSymbols; i++) {
            if (i < 256) {
                writer.writeBits(1, 1); // sym 8 is 1
            } else {
                writer.writeBits(0, 1); // sym 0 is 0
            }
        }
    }
}
