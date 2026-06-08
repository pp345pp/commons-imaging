package org.apache.commons.imaging.formats.jpeg;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.apache.commons.imaging.formats.tiff.write.TiffImageWriterLossy;

import org.apache.commons.imaging.AbstractImageWriter;

public class JpegImageWriter implements AbstractImageWriter<JpegImagingParameters> {

    private static final int[] ZIGZAG = {
        0, 1, 5, 6, 14, 15, 27, 28,
        2, 4, 7, 13, 16, 26, 29, 42,
        3, 8, 12, 17, 25, 30, 41, 43,
        9, 11, 18, 24, 31, 40, 44, 53,
        10, 19, 23, 32, 39, 45, 52, 54,
        20, 22, 33, 38, 46, 51, 55, 60,
        21, 34, 37, 47, 50, 56, 59, 61,
        35, 36, 48, 49, 57, 58, 62, 63
    };

    private static final int[] STD_LUMA_Q = {
        16, 11, 10, 16, 24, 40, 51, 61,
        12, 12, 14, 19, 26, 58, 60, 55,
        14, 13, 16, 24, 40, 57, 69, 56,
        14, 17, 22, 29, 51, 87, 80, 62,
        18, 22, 37, 56, 68,109,103, 77,
        24, 35, 55, 64, 81,104,113, 92,
        49, 64, 78, 87,103,121,120,101,
        72, 92, 95, 98,112,100,103, 99
    };

    private static final int[] STD_CHROMA_Q = {
        17, 18, 24, 47, 99, 99, 99, 99,
        18, 21, 26, 66, 99, 99, 99, 99,
        24, 26, 56, 99, 99, 99, 99, 99,
        47, 66, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99,
        99, 99, 99, 99, 99, 99, 99, 99
    };

    private static final int[] DC_LUMA_BITS = { 0, 1, 5, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0 };
    private static final int[] DC_LUMA_VALS = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };
    
    private static final int[] AC_LUMA_BITS = { 0, 2, 1, 3, 3, 2, 4, 3, 5, 5, 4, 4, 0, 0, 1, 125 };
    private static final int[] AC_LUMA_VALS = {
        0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12,
        0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61, 0x07,
        0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xa1, 0x08,
        0x23, 0x42, 0xb1, 0xc1, 0x15, 0x52, 0xd1, 0xf0,
        0x24, 0x33, 0x62, 0x72, 0x82, 0x09, 0x0a, 0x16,
        0x17, 0x18, 0x19, 0x1a, 0x25, 0x26, 0x27, 0x28,
        0x29, 0x2a, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39,
        0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
        0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59,
        0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69,
        0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79,
        0x7a, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89,
        0x8a, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98,
        0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xa7,
        0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4, 0xb5, 0xb6,
        0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3, 0xc4, 0xc5,
        0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2, 0xd3, 0xd4,
        0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda, 0xe1, 0xe2,
        0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9, 0xea,
        0xf1, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8,
        0xf9, 0xfa
    };

    private static final int[] DC_CHROMA_BITS = { 0, 3, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0 };
    private static final int[] DC_CHROMA_VALS = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 };

    private static final int[] AC_CHROMA_BITS = { 0, 2, 1, 2, 4, 4, 3, 4, 7, 5, 4, 4, 0, 1, 2, 119 };
    private static final int[] AC_CHROMA_VALS = {
        0x00, 0x01, 0x02, 0x03, 0x11, 0x04, 0x05, 0x21,
        0x31, 0x06, 0x12, 0x41, 0x51, 0x07, 0x61, 0x71,
        0x13, 0x22, 0x32, 0x81, 0x08, 0x14, 0x42, 0x91,
        0xa1, 0xb1, 0xc1, 0x09, 0x23, 0x33, 0x52, 0xf0,
        0x15, 0x62, 0x72, 0xd1, 0x0a, 0x16, 0x24, 0x34,
        0xe1, 0x25, 0xf1, 0x17, 0x18, 0x19, 0x1a, 0x26,
        0x27, 0x28, 0x29, 0x2a, 0x35, 0x36, 0x37, 0x38,
        0x39, 0x3a, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48,
        0x49, 0x4a, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58,
        0x59, 0x5a, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68,
        0x69, 0x6a, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78,
        0x79, 0x7a, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87,
        0x88, 0x89, 0x8a, 0x92, 0x93, 0x94, 0x95, 0x96,
        0x97, 0x98, 0x99, 0x9a, 0xa2, 0xa3, 0xa4, 0xa5,
        0xa6, 0xa7, 0xa8, 0xa9, 0xaa, 0xb2, 0xb3, 0xb4,
        0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xc2, 0xc3,
        0xc4, 0xc5, 0xc6, 0xc7, 0xc8, 0xc9, 0xca, 0xd2,
        0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda,
        0xe2, 0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8, 0xe9,
        0xea, 0xf2, 0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xf8,
        0xf9, 0xfa
    };

    private static class HuffmanTable {
        int[] codes = new int[256];
        int[] sizes = new int[256];
        
        HuffmanTable(int[] bits, int[] vals) {
            int code = 0;
            int k = 0;
            for (int i = 0; i < 16; i++) {
                for (int j = 0; j < bits[i]; j++) {
                    codes[vals[k]] = code;
                    sizes[vals[k]] = i + 1;
                    code++;
                    k++;
                }
                code <<= 1;
            }
        }
    }

    private HuffmanTable dcLumaTable = new HuffmanTable(DC_LUMA_BITS, DC_LUMA_VALS);
    private HuffmanTable acLumaTable = new HuffmanTable(AC_LUMA_BITS, AC_LUMA_VALS);
    private HuffmanTable dcChromaTable = new HuffmanTable(DC_CHROMA_BITS, DC_CHROMA_VALS);
    private HuffmanTable acChromaTable = new HuffmanTable(AC_CHROMA_BITS, AC_CHROMA_VALS);

    private static class BitWriter {
        OutputStream os;
        int buffer;
        int bits;

        BitWriter(OutputStream os) {
            this.os = os;
        }

        void write(int value, int numBits) throws IOException {
            buffer = (buffer << numBits) | (value & ((1 << numBits) - 1));
            bits += numBits;
            while (bits >= 8) {
                int b = (buffer >> (bits - 8)) & 0xFF;
                os.write(b);
                if (b == 0xFF) {
                    os.write(0x00);
                }
                bits -= 8;
            }
        }

        void flush() throws IOException {
            if (bits > 0) {
                int b = (buffer << (8 - bits)) | ((1 << (8 - bits)) - 1);
                os.write(b);
                if (b == 0xFF) {
                    os.write(0x00);
                }
                bits = 0;
            }
        }
    }

    private int[] scaleQuantizationTable(int[] table, int quality) {
        int[] result = new int[64];
        int scale;
        if (quality <= 0) quality = 1;
        if (quality > 100) quality = 100;
        if (quality < 50) {
            scale = 5000 / quality;
        } else {
            scale = 200 - quality * 2;
        }
        for (int i = 0; i < 64; i++) {
            int val = (table[i] * scale + 50) / 100;
            if (val < 1) val = 1;
            if (val > 255) val = 255;
            result[i] = val;
        }
        return result;
    }

    private void writeMarker(OutputStream os, int marker) throws IOException {
        os.write(0xFF);
        os.write(marker);
    }

    @Override
    public void writeImage(final BufferedImage src, final OutputStream os, JpegImagingParameters params) throws ImagingException, IOException {
        if (params == null) {
            params = new JpegImagingParameters();
        }
        
        int width = src.getWidth();
        int height = src.getHeight();
        boolean isGrayscale = src.getType() == BufferedImage.TYPE_BYTE_GRAY;
        int numComponents = isGrayscale ? 1 : 3;

        int quality = params.getQuality();
        int[] lumaQ = scaleQuantizationTable(STD_LUMA_Q, quality);
        int[] chromaQ = scaleQuantizationTable(STD_CHROMA_Q, quality);

        writeMarker(os, 0xD8); // SOI

        // APP0 JFIF
        writeMarker(os, 0xE0);
        os.write(0x00); os.write(16); // length
        os.write(new byte[] { 'J', 'F', 'I', 'F', 0 });
        os.write(1); os.write(1); // version
        os.write(0); // units
        os.write(0); os.write(1); // x density
        os.write(0); os.write(1); // y density
        os.write(0); os.write(0); // thumbnail

        // APP1 EXIF
        if (params.getExif() != null) {
            TiffOutputSet exif = params.getExif();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            TiffImageWriterLossy writer = new TiffImageWriterLossy(exif.byteOrder);
            writer.write(baos, exif);
            byte[] exifBytes = baos.toByteArray();
            
            writeMarker(os, 0xE1);
            int len = exifBytes.length + 8;
            os.write((len >> 8) & 0xFF);
            os.write(len & 0xFF);
            os.write(new byte[] { 'E', 'x', 'i', 'f', 0, 0 });
            os.write(exifBytes);
        }

        // APP2 ICC Profile
        if (params.getIccProfile() != null) {
            byte[] icc = params.getIccProfile();
            writeMarker(os, 0xE2);
            int len = icc.length + 16;
            os.write((len >> 8) & 0xFF);
            os.write(len & 0xFF);
            os.write(new byte[] { 'I', 'C', 'C', '_', 'P', 'R', 'O', 'F', 'I', 'L', 'E', 0 });
            os.write(1); os.write(1); // chunk 1 of 1
            os.write(icc);
        }

        // DQT
        writeMarker(os, 0xDB);
        int dqtLen = 2 + (isGrayscale ? 65 : 130);
        os.write((dqtLen >> 8) & 0xFF);
        os.write(dqtLen & 0xFF);
        os.write(0); // Pq, Tq (0, 0)
        for (int i = 0; i < 64; i++) os.write(lumaQ[ZIGZAG[i]]);
        if (!isGrayscale) {
            os.write(1); // Pq, Tq (0, 1)
            for (int i = 0; i < 64; i++) os.write(chromaQ[ZIGZAG[i]]);
        }

        // SOF0
        writeMarker(os, 0xC0);
        int sofLen = 8 + 3 * numComponents;
        os.write((sofLen >> 8) & 0xFF);
        os.write(sofLen & 0xFF);
        os.write(8); // precision
        os.write((height >> 8) & 0xFF);
        os.write(height & 0xFF);
        os.write((width >> 8) & 0xFF);
        os.write(width & 0xFF);
        os.write(numComponents);
        os.write(1); // ID
        os.write(0x11); // H, V sampling
        os.write(0); // Q table
        if (!isGrayscale) {
            os.write(2); // ID
            os.write(0x11);
            os.write(1);
            os.write(3); // ID
            os.write(0x11);
            os.write(1);
        }

        // DHT
        writeMarker(os, 0xC4);
        int dhtLen = 2 + 17 + DC_LUMA_VALS.length + 17 + AC_LUMA_VALS.length;
        if (!isGrayscale) dhtLen += 17 + DC_CHROMA_VALS.length + 17 + AC_CHROMA_VALS.length;
        os.write((dhtLen >> 8) & 0xFF);
        os.write(dhtLen & 0xFF);
        
        os.write(0x00);
        for (int b : DC_LUMA_BITS) os.write(b);
        for (int v : DC_LUMA_VALS) os.write(v);
        
        os.write(0x10);
        for (int b : AC_LUMA_BITS) os.write(b);
        for (int v : AC_LUMA_VALS) os.write(v);
        
        if (!isGrayscale) {
            os.write(0x01);
            for (int b : DC_CHROMA_BITS) os.write(b);
            for (int v : DC_CHROMA_VALS) os.write(v);
            
            os.write(0x11);
            for (int b : AC_CHROMA_BITS) os.write(b);
            for (int v : AC_CHROMA_VALS) os.write(v);
        }

        // SOS
        writeMarker(os, 0xDA);
        int sosLen = 6 + 2 * numComponents;
        os.write((sosLen >> 8) & 0xFF);
        os.write(sosLen & 0xFF);
        os.write(numComponents);
        os.write(1); os.write(0x00);
        if (!isGrayscale) {
            os.write(2); os.write(0x11);
            os.write(3); os.write(0x11);
        }
        os.write(0); os.write(63); os.write(0);

        // Encode data
        BitWriter bw = new BitWriter(os);
        int prevDcY = 0, prevDcCb = 0, prevDcCr = 0;
        
        float[][] dctMatrix = new float[8][8];
        float[][] tempMatrix = new float[8][8];
        
        for (int y = 0; y < height; y += 8) {
            for (int x = 0; x < width; x += 8) {
                float[] yBlock = new float[64];
                float[] cbBlock = new float[64];
                float[] crBlock = new float[64];
                
                for (int by = 0; by < 8; by++) {
                    for (int bx = 0; bx < 8; bx++) {
                        int px = Math.min(x + bx, width - 1);
                        int py = Math.min(y + by, height - 1);
                        int rgb = src.getRGB(px, py);
                        
                        if (isGrayscale) {
                            int r = rgb & 0xFF;
                            yBlock[by * 8 + bx] = r - 128.0f;
                        } else {
                            int r = (rgb >> 16) & 0xFF;
                            int g = (rgb >> 8) & 0xFF;
                            int b = rgb & 0xFF;
                            yBlock[by * 8 + bx] = (0.299f * r + 0.587f * g + 0.114f * b) - 128.0f;
                            cbBlock[by * 8 + bx] = -0.1687f * r - 0.3313f * g + 0.5f * b;
                            crBlock[by * 8 + bx] = 0.5f * r - 0.4187f * g - 0.0813f * b;
                        }
                    }
                }
                
                prevDcY = encodeBlock(bw, yBlock, lumaQ, prevDcY, dcLumaTable, acLumaTable, dctMatrix, tempMatrix);
                if (!isGrayscale) {
                    prevDcCb = encodeBlock(bw, cbBlock, chromaQ, prevDcCb, dcChromaTable, acChromaTable, dctMatrix, tempMatrix);
                    prevDcCr = encodeBlock(bw, crBlock, chromaQ, prevDcCr, dcChromaTable, acChromaTable, dctMatrix, tempMatrix);
                }
            }
        }
        bw.flush();
        writeMarker(os, 0xD9); // EOI
    }

    private int encodeBlock(BitWriter bw, float[] block, int[] qTable, int prevDc, HuffmanTable dcTable, HuffmanTable acTable, float[][] dctMatrix, float[][] tempMatrix) throws IOException {
        fdct(block, dctMatrix, tempMatrix);
        int[] quantized = new int[64];
        for (int i = 0; i < 64; i++) {
            quantized[i] = Math.round(block[i] / qTable[i]);
        }
        
        int[] zz = new int[64];
        for (int i = 0; i < 64; i++) {
            zz[i] = quantized[ZIGZAG[i]];
        }
        
        int dc = zz[0];
        int diff = dc - prevDc;
        encodeValue(bw, diff, dcTable);
        
        int zeroCount = 0;
        for (int i = 1; i < 64; i++) {
            int ac = zz[i];
            if (ac == 0) {
                zeroCount++;
            } else {
                while (zeroCount >= 16) {
                    bw.write(acTable.codes[0xF0], acTable.sizes[0xF0]);
                    zeroCount -= 16;
                }
                int size = getValueSize(ac);
                int symbol = (zeroCount << 4) | size;
                bw.write(acTable.codes[symbol], acTable.sizes[symbol]);
                writeBits(bw, ac, size);
                zeroCount = 0;
            }
        }
        if (zeroCount > 0) {
            bw.write(acTable.codes[0x00], acTable.sizes[0x00]);
        }
        
        return dc;
    }

    private void encodeValue(BitWriter bw, int val, HuffmanTable table) throws IOException {
        int size = getValueSize(val);
        bw.write(table.codes[size], table.sizes[size]);
        writeBits(bw, val, size);
    }

    private int getValueSize(int val) {
        if (val < 0) val = -val;
        int size = 0;
        while (val > 0) {
            size++;
            val >>= 1;
        }
        return size;
    }

    private void writeBits(BitWriter bw, int val, int size) throws IOException {
        if (size == 0) return;
        if (val < 0) {
            val--;
            val &= (1 << size) - 1;
        }
        bw.write(val, size);
    }

    private void fdct(float[] block, float[][] matrix, float[][] temp) {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                matrix[i][j] = block[i * 8 + j];
            }
        }
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                float sum = 0;
                for (int k = 0; k < 8; k++) {
                    sum += matrix[i][k] * Math.cos((2 * k + 1) * j * Math.PI / 16.0);
                }
                float alpha = (j == 0) ? (float)(1.0 / Math.sqrt(2)) : 1.0f;
                temp[i][j] = sum * alpha * 0.5f;
            }
        }
        for (int j = 0; j < 8; j++) {
            for (int i = 0; i < 8; i++) {
                float sum = 0;
                for (int k = 0; k < 8; k++) {
                    sum += temp[k][j] * Math.cos((2 * k + 1) * i * Math.PI / 16.0);
                }
                float alpha = (i == 0) ? (float)(1.0 / Math.sqrt(2)) : 1.0f;
                block[i * 8 + j] = sum * alpha * 0.5f;
            }
        }
    }
}
