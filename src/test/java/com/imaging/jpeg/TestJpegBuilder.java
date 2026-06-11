package com.imaging.jpeg;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class TestJpegBuilder {

    public enum CorruptionType {
        OFFSET_OUT_OF_BOUNDS,
        LENGTH_OUT_OF_BOUNDS,
        CORRUPT_TIFF_STRUCTURE
    }

    public static byte[] buildCorruptedJpeg(CorruptionType type) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // SOI
        dos.writeShort(0xFFD8);

        // APP0
        dos.writeShort(0xFFE0);
        dos.writeShort(16); // length
        dos.writeBytes("JFIF\0");
        dos.writeShort(0x0101); // version
        dos.writeByte(0); // units
        dos.writeShort(1); // X density
        dos.writeShort(1); // Y density
        dos.writeByte(0); // thumbnail width
        dos.writeByte(0); // thumbnail height

        // APP1 EXIF
        ByteArrayOutputStream exifBaos = new ByteArrayOutputStream();
        DataOutputStream exifDos = new DataOutputStream(exifBaos);
        exifDos.writeBytes("Exif\0\0");
        
        // TIFF Header (MM)
        exifDos.writeShort(0x4D4D); // MM
        exifDos.writeShort(42); // 42
        exifDos.writeInt(8); // Offset to 0th IFD

        // 0th IFD (at offset 8)
        exifDos.writeShort(0); // 0 entries
        int ifd1Offset = 8 + 2 + 4; // 14
        exifDos.writeInt(ifd1Offset);

        // 1st IFD (Thumbnail IFD)
        exifDos.writeShort(2); // 2 entries
        
        // Entry 1: JPEGInterchangeFormat (0x0201)
        exifDos.writeShort(0x0201);
        exifDos.writeShort(4); // LONG
        exifDos.writeInt(1); // count
        
        int thumbOffset = 14 + 2 + 24 + 4; // 44
        
        if (type == CorruptionType.OFFSET_OUT_OF_BOUNDS) {
            exifDos.writeInt(100000);
        } else {
            exifDos.writeInt(thumbOffset);
        }

        // Entry 2: JPEGInterchangeFormatLength (0x0202)
        exifDos.writeShort(0x0202);
        exifDos.writeShort(4); // LONG
        exifDos.writeInt(1); // count
        if (type == CorruptionType.LENGTH_OUT_OF_BOUNDS) {
            exifDos.writeInt(100000);
        } else {
            exifDos.writeInt(10);
        }

        // Next IFD Offset
        if (type == CorruptionType.CORRUPT_TIFF_STRUCTURE) {
            exifDos.writeInt(0xFFFFFFFF);
        } else {
            exifDos.writeInt(0);
        }

        // Thumbnail data
        exifDos.write(new byte[10]);

        byte[] exifData = exifBaos.toByteArray();
        dos.writeShort(0xFFE1);
        dos.writeShort(exifData.length + 2); // length includes size field
        dos.write(exifData);

        // DQT
        dos.writeShort(0xFFDB);
        dos.writeShort(67);
        dos.writeByte(0);
        dos.write(new byte[64]);

        // SOF0
        dos.writeShort(0xFFC0);
        dos.writeShort(17);
        dos.writeByte(8);
        dos.writeShort(1);
        dos.writeShort(1);
        dos.writeByte(3);
        dos.writeByte(1); dos.writeByte(0x11); dos.writeByte(0);
        dos.writeByte(2); dos.writeByte(0x11); dos.writeByte(0);
        dos.writeByte(3); dos.writeByte(0x11); dos.writeByte(0);

        // DHT
        dos.writeShort(0xFFC4);
        dos.writeShort(19);
        dos.writeByte(0);
        dos.write(new byte[16]);

        // SOS
        dos.writeShort(0xFFDA);
        dos.writeShort(12);
        dos.writeByte(3);
        dos.writeByte(1); dos.writeByte(0);
        dos.writeByte(2); dos.writeByte(0);
        dos.writeByte(3); dos.writeByte(0);
        dos.writeByte(0); dos.writeByte(63); dos.writeByte(0);

        // Image data
        dos.writeByte(0x00);

        // EOI
        dos.writeShort(0xFFD9);

        return baos.toByteArray();
    }
}
