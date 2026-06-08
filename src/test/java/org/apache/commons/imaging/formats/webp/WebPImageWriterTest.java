package org.apache.commons.imaging.formats.webp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;

import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.junit.jupiter.api.Test;

public class WebPImageWriterTest {

    @Test
    public void testWriteAndRead() throws Exception {
        BufferedImage src = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                src.setRGB(x, y, 0xFF00FF00); // Green
            }
        }

        WebPImagingParameters params = new WebPImagingParameters();
        
        TiffOutputSet exif = new TiffOutputSet();
        TiffOutputDirectory rootDir = exif.getOrCreateRootDirectory();
        rootDir.add(ExifTagConstants.EXIF_TAG_SOFTWARE, "Commons Imaging");
        params.setExif(exif);
        params.setXmpXml("<xmp>Test</xmp>");
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new WebPImageWriter().writeImage(src, baos, params);
        
        byte[] webpBytes = baos.toByteArray();
        
        BufferedImage dst = Imaging.getBufferedImage(webpBytes);
        assertEquals(10, dst.getWidth());
        assertEquals(10, dst.getHeight());
        assertEquals(0xFF00FF00, dst.getRGB(5, 5));
    }
}