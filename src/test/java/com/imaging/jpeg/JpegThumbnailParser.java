package com.imaging.jpeg;

import org.apache.commons.imaging.ImagingFormatException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class JpegThumbnailParser {

    private static final int JPEG_SOI = 0xFFD8;
    private static final int JPEG_APP1 = 0xFFE1;
    private static final int JPEG_EOI = 0xFFD9;
    private static final byte[] EXIF_IDENTIFIER = {'E', 'x', 'i', 'f', 0, 0};
    private static final int TIFF_BYTE_ORDER_II = 0x4949;
    private static final int TIFF_BYTE_ORDER_MM = 0x4D4D;
    private static final int TIFF_MAGIC = 42;
    private static final int TAG_THUMBNAIL_OFFSET = 0x0201;
    private static final int TAG_THUMBNAIL_LENGTH = 0x0202;

    public static void extractThumbnail(byte[] jpegData) {
        if (jpegData == null || jpegData.length < 4) {
            throw new ImagingFormatException("Invalid JPEG data: too short");
        }

        ByteBuffer buffer = ByteBuffer.wrap(jpegData);
        buffer.order(ByteOrder.BIG_ENDIAN);

        int soi = buffer.getShort() & 0xFFFF;
        if (soi != JPEG_SOI) {
            throw new ImagingFormatException("Not a valid JPEG: missing SOI marker");
        }

        byte[] exifData = findApp1Exif(buffer);
        if (exifData == null) {
            throw new ImagingFormatException("No EXIF data found in JPEG");
        }

        parseTiffForThumbnail(exifData);
    }

    private static byte[] findApp1Exif(ByteBuffer buffer) {
        while (buffer.remaining() >= 4) {
            int marker = buffer.getShort() & 0xFFFF;

            if (marker == JPEG_EOI) {
                return null;
            }

            if (marker == JPEG_APP1) {
                int segmentLength = buffer.getShort() & 0xFFFF;
                if (segmentLength < 2) {
                    throw new ImagingFormatException("Invalid APP1 segment length");
                }

                int segmentDataLength = segmentLength - 2;
                if (buffer.remaining() < segmentDataLength) {
                    throw new ImagingFormatException("Truncated APP1 segment");
                }

                byte[] segmentData = new byte[segmentDataLength];
                buffer.get(segmentData);

                if (isExifIdentifier(segmentData)) {
                    byte[] tiffData = new byte[segmentDataLength - 6];
                    System.arraycopy(segmentData, 6, tiffData, 0, tiffData.length);
                    return tiffData;
                }
            } else if ((marker & 0xFF00) == 0xFF00) {
                int segmentLength = buffer.getShort() & 0xFFFF;
                if (segmentLength < 2) {
                    throw new ImagingFormatException("Invalid segment length");
                }
                int skipLength = segmentLength - 2;
                if (buffer.remaining() < skipLength) {
                    throw new ImagingFormatException("Truncated segment");
                }
                buffer.position(buffer.position() + skipLength);
            } else {
                return null;
            }
        }
        return null;
    }

    private static boolean isExifIdentifier(byte[] segmentData) {
        if (segmentData.length < 6) {
            return false;
        }
        for (int i = 0; i < 6; i++) {
            if (segmentData[i] != EXIF_IDENTIFIER[i]) {
                return false;
            }
        }
        return true;
    }

    private static void parseTiffForThumbnail(byte[] tiffData) {
        if (tiffData.length < 8) {
            throw new ImagingFormatException("TIFF data too short");
        }

        ByteBuffer tiffBuffer = ByteBuffer.wrap(tiffData);
        int byteOrderMarker = tiffBuffer.getShort() & 0xFFFF;

        ByteOrder byteOrder;
        if (byteOrderMarker == TIFF_BYTE_ORDER_II) {
            byteOrder = ByteOrder.LITTLE_ENDIAN;
        } else if (byteOrderMarker == TIFF_BYTE_ORDER_MM) {
            byteOrder = ByteOrder.BIG_ENDIAN;
        } else {
            throw new ImagingFormatException("Invalid TIFF byte order");
        }
        tiffBuffer.order(byteOrder);

        int magic = tiffBuffer.getShort() & 0xFFFF;
        if (magic != TIFF_MAGIC) {
            throw new ImagingFormatException("Invalid TIFF magic number");
        }

        int ifdOffset = readInt(tiffBuffer);
        if (ifdOffset < 8 || ifdOffset >= tiffData.length) {
            throw new ImagingFormatException("Invalid IFD offset");
        }

        parseIfd(tiffBuffer, tiffData, ifdOffset);
    }

    private static void parseIfd(ByteBuffer tiffBuffer, byte[] tiffData, int ifdOffset) {
        tiffBuffer.position(ifdOffset);

        int numEntries = readShort(tiffBuffer);
        if (numEntries == 0 || ifdOffset + 2 + numEntries * 12 + 4 > tiffData.length) {
            throw new ImagingFormatException("Invalid IFD entry count");
        }

        int thumbnailOffset = -1;
        int thumbnailLength = -1;

        for (int i = 0; i < numEntries; i++) {
            int tag = readShort(tiffBuffer);
            int type = readShort(tiffBuffer);
            int count = readInt(tiffBuffer);
            int valueOffset = readInt(tiffBuffer);

            if (tag == TAG_THUMBNAIL_OFFSET) {
                thumbnailOffset = valueOffset;
            } else if (tag == TAG_THUMBNAIL_LENGTH) {
                thumbnailLength = valueOffset;
            }
        }

        int nextIfdOffset = readInt(tiffBuffer);

        if (nextIfdOffset == 0xFFFFFFFF) {
            throw new ImagingFormatException("Thumbnail TIFF structure corrupted: invalid Next IFD offset");
        }

        if (thumbnailOffset == -1 || thumbnailLength == -1) {
            return;
        }

        if (thumbnailOffset < 0 || thumbnailOffset >= tiffData.length) {
            throw new ImagingFormatException("Thumbnail offset points outside file: offset=%d, fileSize=%d", thumbnailOffset, tiffData.length);
        }

        if (thumbnailOffset + thumbnailLength > tiffData.length) {
            throw new ImagingFormatException("Thumbnail length exceeds file size: offset=%d, length=%d, fileSize=%d",
                    thumbnailOffset, thumbnailLength, tiffData.length);
        }
    }

    private static int readShort(ByteBuffer buffer) {
        return buffer.getShort() & 0xFFFF;
    }

    private static int readInt(ByteBuffer buffer) {
        return buffer.getInt() & 0xFFFFFFFF;
    }
}
