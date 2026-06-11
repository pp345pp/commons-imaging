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
package com.imaging.jpeg;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Builds an in-memory JPEG byte array that contains the structural segments
 * required to carry an EXIF thumbnail:
 *
 * <pre>
 *   SOI + APP0 (JFIF) + APP1 (Exif/TIFF with thumbnail tags)
 *   + DQT + SOF0 + DHT + SOS + [dummy scan data] + EOI
 * </pre>
 *
 * <p>The thumbnail bytes referenced by the {@code JPEGInterchangeFormat} /
 * {@code JPEGInterchangeFormatLength} TIFF tags can be deliberately corrupted
 * using one of the {@link CorruptionMode} options.  The resulting JPEG is
 * suitable as a deterministic test input for
 * {@link JpegThumbnailParser#extractThumbnail(byte[])}.</p>
 */
public final class TestJpegBuilder {

    /** Different ways a thumbnail reference inside the TIFF can be broken. */
    public enum CorruptionMode {
        /** A valid JPEG with an intact thumbnail - the happy path. */
        VALID,
        /** The thumbnail offset is set past the end of the byte array. */
        OFFSET_OUT_OF_BOUNDS,
        /**
         * The offset is still inside the file, but (offset + length) exceeds
         * the end of the byte array.
         */
        LENGTH_EXCEEDS_FILE,
        /**
         * The "Next IFD" pointer is set to {@code 0xFFFFFFFF} so the parser
         * aborts while walking the thumbnail IFD.
         */
        NEXT_IFD_INVALID
    }

    private static final byte[] JFIF_IDENTIFIER = { 'J', 'F', 'I', 'F', 0 };
    private static final byte[] EXIF_IDENTIFIER = { 'E', 'x', 'i', 'f', 0, 0 };

    /** TIFF tag id for the offset of the thumbnail JPEG bytes. */
    public static final int TAG_JPEG_INTERCHANGE_FORMAT = 0x0201;

    /** TIFF tag id for the length of the thumbnail JPEG bytes. */
    public static final int TAG_JPEG_INTERCHANGE_FORMAT_LENGTH = 0x0202;

    /** TIFF tag id for the compression scheme. */
    public static final int TAG_COMPRESSION = 0x0103;

    private static final int IFD_ENTRY_SIZE = 12;
    private static final int TIFF_HEADER_SIZE = 8;

    private TestJpegBuilder() {
    }

    /**
     * Convenience for building a JPEG with the requested corruption applied.
     */
    public static byte[] buildJpeg(final CorruptionMode mode) {
        return new TestJpegBuilder().createJpegWithExifThumbnail(mode);
    }

    private byte[] createJpegWithExifThumbnail(final CorruptionMode mode) {
        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();

            // SOI marker
            writeMarker(out, 0xFFD8);

            // APP0 JFIF segment (minimal).
            final byte[] app0 = buildApp0();
            out.write(app0);

            // APP1 EXIF segment - the interesting part.
            final byte[] app1 = buildApp1Exif(mode);
            out.write(app1);

            // DQT (luminance table, minimal placeholder).
            out.write(buildDqt());

            // SOF0 (baseline, 8-bit, arbitrary small frame).
            out.write(buildSof0());

            // DHT (placeholder table, not actually used for decoding).
            out.write(buildDht());

            // SOS + a single byte of compressed data.
            out.write(buildSos());

            // EOI marker
            writeMarker(out, 0xFFD9);

            return out.toByteArray();
        } catch (final IOException e) {
            throw new IllegalStateException("In-memory JPEG construction failed", e);
        }
    }

    private static void writeMarker(final ByteArrayOutputStream out, final int marker) {
        out.write((marker >> 8) & 0xFF);
        out.write(marker & 0xFF);
    }

    private static byte[] buildApp0() {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0xFF);
        baos.write(0xE0);
        // length placeholder
        baos.write(0);
        baos.write(0);
        try {
            baos.write(JFIF_IDENTIFIER);
        } catch (final IOException ignored) {
            // in-memory
        }
        // version + units + density + thumbnail dims
        baos.write(1);
        baos.write(2);
        baos.write(0);
        baos.write(0x01);
        baos.write(0);
        baos.write(0x01);
        baos.write(0);
        baos.write(0);
        final byte[] bytes = baos.toByteArray();
        final int length = bytes.length - 2; // length field includes itself
        bytes[2] = (byte) ((length >> 8) & 0xFF);
        bytes[3] = (byte) (length & 0xFF);
        return bytes;
    }

    /**
     * Builds the APP1/Exif segment containing a TIFF structure with a
     * thumbnail IFD.  Offsets in the TIFF are relative to the TIFF header
     * start, which is the standard Exif layout.
     */
    private static byte[] buildApp1Exif(final CorruptionMode mode) {
        // TIFF structure layout (offsets relative to tiffHeaderOffset):
        //   0: 'II' + 42 (u16) + IFD0 offset (u32)
        //   8: IFD0 (2 entries + next IFD pointer)
        //       entries point to thumbnail offset/length stored elsewhere.
        //   next: values area containing the u32 for thumbnail offset and length
        //   last: the thumbnail JPEG bytes (SOI + EOI, a valid tiny JPEG) OR
        //         a broken placeholder when length is overridden.

        // Tiny valid JPEG thumbnail payload (SOI + EOI).
        final byte[] thumbnailJpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9 };

        // Thumbnail TIFF layout.
        final ByteArrayOutputStream tiff = new ByteArrayOutputStream();

        // TIFF header: 'II' little endian, magic 42, first IFD at offset 8.
        writeU8(tiff, 'I');
        writeU8(tiff, 'I');
        writeU16LE(tiff, 42);
        writeU32LE(tiff, TIFF_HEADER_SIZE); // IFD0 offset

        // IFD0: 2 entries.
        // After the 12-byte entry fields we store the thumbnail offset/length
        // values.  Since they fit inline we just use the value slot.
        final int ifd0Offset = TIFF_HEADER_SIZE;
        final int ifd0EntryCount = 2;
        final int ifd0Size = 2 + ifd0EntryCount * IFD_ENTRY_SIZE + 4;

        // Layout the positions we will point to.
        final int ifd0End = ifd0Offset + ifd0Size;

        // We'll place a small extra IFD (IFD1) after IFD0, whose entries
        // contain JPEGInterchangeFormat / JPEGInterchangeFormatLength.
        // To exercise the Next-IFD walk we put IFD1 after IFD0.
        final int ifd1Offset = ifd0End;
        final int ifd1EntryCount = 3; // Compression + offset + length
        final int ifd1Size = 2 + ifd1EntryCount * IFD_ENTRY_SIZE + 4;
        final int ifd1End = ifd1Offset + ifd1Size;

        // Thumbnail bytes follow IFD1.
        final int thumbnailOffset = ifd1End;
        final long effectiveThumbnailLength = thumbnailJpeg.length;

        // Compute what will actually be written at offset+length.
        long effectiveOffset = thumbnailOffset;
        long effectiveLength = effectiveThumbnailLength;
        int ifd1NextPointer = 0; // by default there is no further IFD

        switch (mode) {
            case VALID:
                break;
            case OFFSET_OUT_OF_BOUNDS:
                effectiveOffset = 0x7FFFFFFFL;
                break;
            case LENGTH_EXCEEDS_FILE:
                effectiveLength = 0x7FFFFFFFL;
                break;
            case NEXT_IFD_INVALID:
                ifd1NextPointer = 0xFFFFFFFF;
                break;
            default:
                throw new IllegalStateException("Unknown corruption mode: " + mode);
        }

        // --- IFD0 entries ---
        writeU16LE(tiff, ifd0EntryCount);
        // Entry 1: Compression = 6 (JPEG) - value fits inline (u16).
        writeIfdEntry(tiff, TAG_COMPRESSION, 3 /* SHORT */, 1, 6);
        // Entry 2: any innocuous tag, keep as padding. Compression duplicated as long: not valid; use YCbCrSubSampling as padding with a dummy value.
        // We simply add another SHORT tag, StripOffsets (0x0111) with a u32 inline pointing nowhere in particular (never read).
        writeIfdEntry(tiff, 0x0111, 4 /* LONG */, 1, thumbnailOffset);
        // Next IFD: points to IFD1.
        writeU32LE(tiff, ifd1Offset);

        // --- IFD1 entries ---
        writeU16LE(tiff, ifd1EntryCount);
        writeIfdEntry(tiff, TAG_COMPRESSION, 3 /* SHORT */, 1, 6);
        writeIfdEntry(tiff, TAG_JPEG_INTERCHANGE_FORMAT, 4 /* LONG */, 1, (int) effectiveOffset & 0xFFFFFFFF);
        writeIfdEntry(tiff, TAG_JPEG_INTERCHANGE_FORMAT_LENGTH, 4 /* LONG */, 1, (int) effectiveLength & 0xFFFFFFFF);
        // Next IFD pointer (possibly corrupted).
        writeU32LE(tiff, ifd1NextPointer);

        // --- Thumbnail bytes (written unconditionally; offsets/lengths
        // above control whether the parser thinks they are valid). ---
        try {
            tiff.write(thumbnailJpeg);
        } catch (final IOException ignored) {
            // in-memory write cannot fail
        }
        // Defensive validation; values above are always non-negative.
        if (effectiveOffset < 0 || effectiveLength < 0) {
            throw new IllegalStateException("Invalid thumbnail offset or length");
        }

        // Build the APP1 wrapper.
        final byte[] tiffBytes = tiff.toByteArray();
        final ByteArrayOutputStream app1 = new ByteArrayOutputStream();
        app1.write(0xFF);
        app1.write(0xE1);
        // length placeholder
        app1.write(0);
        app1.write(0);
        try {
            app1.write(EXIF_IDENTIFIER);
            app1.write(tiffBytes);
        } catch (final IOException ignored) {
            // in-memory
        }
        final byte[] result = app1.toByteArray();
        final int length = result.length - 2;
        result[2] = (byte) ((length >> 8) & 0xFF);
        result[3] = (byte) (length & 0xFF);
        return result;
    }

    private static void writeIfdEntry(final ByteArrayOutputStream out,
                                      final int tag,
                                      final int type,
                                      final int count,
                                      final int inlineValue) {
        writeU16LE(out, tag);
        writeU16LE(out, type);
        writeU32LE(out, count);
        writeU32LE(out, inlineValue);
    }

    private static void writeU8(final ByteArrayOutputStream out, final int value) {
        out.write(value & 0xFF);
    }

    private static void writeU16LE(final ByteArrayOutputStream out, final int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    private static void writeU32LE(final ByteArrayOutputStream out, final long value) {
        out.write((int) (value & 0xFF));
        out.write((int) ((value >> 8) & 0xFF));
        out.write((int) ((value >> 16) & 0xFF));
        out.write((int) ((value >> 24) & 0xFF));
    }

    private static byte[] buildDqt() {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0xFF);
        baos.write(0xDB);
        // length = 2 + 1 + 64 = 67
        final int length = 67;
        baos.write((length >> 8) & 0xFF);
        baos.write(length & 0xFF);
        // precision 0 (8-bit), table id 0
        baos.write(0x00);
        for (int i = 0; i < 64; i++) {
            baos.write(1);
        }
        return baos.toByteArray();
    }

    private static byte[] buildSof0() {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0xFF);
        baos.write(0xC0);
        // length = 2 + 1 + 2 + 2 + 1 + 3*components
        final int components = 1;
        final int length = 2 + 1 + 2 + 2 + 1 + 3 * components;
        baos.write((length >> 8) & 0xFF);
        baos.write(length & 0xFF);
        baos.write(8); // precision (bits per sample)
        // height = 8
        baos.write(0);
        baos.write(8);
        // width = 8
        baos.write(0);
        baos.write(8);
        baos.write(components);
        // component Y: id=1, h/v=0x11, qt=0
        baos.write(1);
        baos.write(0x11);
        baos.write(0);
        return baos.toByteArray();
    }

    private static byte[] buildDht() {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0xFF);
        baos.write(0xC4);
        // length = 2 + 1 + 16 + 12*11 = 151
        final int length = 2 + 1 + 16 + 12;
        baos.write((length >> 8) & 0xFF);
        baos.write(length & 0xFF);
        baos.write(0x00); // DC table, id 0
        // 16 counts: put one symbol at code length 1, rest zero
        baos.write(1);
        for (int i = 1; i < 16; i++) {
            baos.write(0);
        }
        // symbols
        baos.write(0);
        for (int i = 1; i < 12; i++) {
            baos.write(i);
        }
        return baos.toByteArray();
    }

    private static byte[] buildSos() {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0xFF);
        baos.write(0xDA);
        // length = 2 + 1 + 2*1 + 3 = 8 (1 component)
        final int length = 8;
        baos.write((length >> 8) & 0xFF);
        baos.write(length & 0xFF);
        baos.write(1); // components
        baos.write(1); // component id
        baos.write(0x00); // DC/AC table bytes combined
        baos.write(0); // spectral start
        baos.write(63); // spectral end
        baos.write(0); // successive approx
        // scan data byte
        baos.write(0x00);
        return baos.toByteArray();
    }
}
