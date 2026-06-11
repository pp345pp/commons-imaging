package com.imaging.jpeg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class TestJpegBuilder {

    private static final byte[] EXIF_IDENTIFIER = {'E', 'x', 'i', 'f', 0, 0};

    public static byte[] buildJpegWithCorruptedThumbnail_OffsetOutOfBounds() throws IOException {
        byte[] tiffData = buildTiffWithThumbnail(ThumbnailCorruptionType.OFFSET_OUT_OF_BOUNDS);
        return buildJpegWithExif(tiffData);
    }

    public static byte[] buildJpegWithCorruptedThumbnail_LengthExceedsFile() throws IOException {
        byte[] tiffData = buildTiffWithThumbnail(ThumbnailCorruptionType.LENGTH_EXCEEDS_FILE);
        return buildJpegWithExif(tiffData);
    }

    public static byte[] buildJpegWithCorruptedThumbnail_InternalTiffCorrupted() throws IOException {
        byte[] tiffData = buildTiffWithThumbnail(ThumbnailCorruptionType.INTERNAL_TIFF_CORRUPTED);
        return buildJpegWithExif(tiffData);
    }

    private enum ThumbnailCorruptionType {
        OFFSET_OUT_OF_BOUNDS,
        LENGTH_EXCEEDS_FILE,
        INTERNAL_TIFF_CORRUPTED
    }

    private static byte[] buildTiffWithThumbnail(ThumbnailCorruptionType corruptionType) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.putShort((short) 0x4D4D);
        buffer.putShort((short) 42);

        int ifdOffset = 8;
        buffer.putInt(ifdOffset);

        int thumbnailDataOffset = 100;
        int thumbnailDataLength = 32;

        buffer.position(ifdOffset);

        int numEntries = 3;
        buffer.putShort((short) numEntries);

        buffer.putShort((short) 0x0201);
        buffer.putShort((short) 4);
        buffer.putInt(1);
        if (corruptionType == ThumbnailCorruptionType.OFFSET_OUT_OF_BOUNDS) {
            buffer.putInt(0xFFFFFFF0);
        } else {
            buffer.putInt(thumbnailDataOffset);
        }

        buffer.putShort((short) 0x0202);
        buffer.putShort((short) 4);
        buffer.putInt(1);
        if (corruptionType == ThumbnailCorruptionType.LENGTH_EXCEEDS_FILE) {
            buffer.putInt(0xFFFFFFF0);
        } else {
            buffer.putInt(thumbnailDataLength);
        }

        buffer.putShort((short) 0x0000);
        buffer.putShort((short) 4);
        buffer.putInt(1);
        if (corruptionType == ThumbnailCorruptionType.INTERNAL_TIFF_CORRUPTED) {
            buffer.putInt(0xFFFFFFFF);
        } else {
            buffer.putInt(0);
        }

        buffer.position(thumbnailDataOffset);
        for (int i = 0; i < thumbnailDataLength; i++) {
            buffer.put((byte) (0xFF & i));
        }

        int tiffSize = thumbnailDataOffset + thumbnailDataLength;
        byte[] tiffBytes = new byte[tiffSize];
        buffer.position(0);
        buffer.get(tiffBytes, 0, tiffSize);

        return tiffBytes;
    }

    private static byte[] buildJpegWithExif(byte[] tiffData) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        baos.write(0xFF);
        baos.write(0xD8);

        byte[] app1Content = new byte[EXIF_IDENTIFIER.length + tiffData.length];
        System.arraycopy(EXIF_IDENTIFIER, 0, app1Content, 0, EXIF_IDENTIFIER.length);
        System.arraycopy(tiffData, 0, app1Content, EXIF_IDENTIFIER.length, tiffData.length);

        int app1Length = 2 + app1Content.length;
        baos.write(0xFF);
        baos.write(0xE1);
        baos.write((app1Length >> 8) & 0xFF);
        baos.write(app1Length & 0xFF);
        baos.write(app1Content);

        baos.write(0xFF);
        baos.write(0xDB);
        baos.write(0x00);
        baos.write(0x43);
        for (int i = 0; i < 64; i++) {
            baos.write(1);
        }

        baos.write(0xFF);
        baos.write(0xC0);
        baos.write(0x00);
        baos.write(0x0B);
        baos.write(8);
        baos.write(0x00);
        baos.write(0x10);
        baos.write(0x00);
        baos.write(0x10);
        baos.write(1);
        baos.write(1);
        baos.write(1);
        baos.write(1);
        baos.write(0x11);
        baos.write(0);

        baos.write(0xFF);
        baos.write(0xC4);
        baos.write(0x00);
        baos.write(0x1F);
        baos.write(0);
        for (int i = 0; i < 12; i++) baos.write(i);
        for (int i = 0; i < 12; i++) baos.write(i);
        baos.write(0x01);
        for (int i = 0; i < 12; i++) baos.write(i);
        for (int i = 0; i < 12; i++) baos.write(i);

        baos.write(0xFF);
        baos.write(0xDA);
        baos.write(0x00);
        baos.write(0x08);
        baos.write(1);
        baos.write(1);
        baos.write(0);
        baos.write(0);
        baos.write(0x3F);
        baos.write(0);

        baos.write(0xFF);
        baos.write(0xD9);

        return baos.toByteArray();
    }
}
