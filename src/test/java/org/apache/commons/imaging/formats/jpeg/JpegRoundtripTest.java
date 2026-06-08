package org.apache.commons.imaging.formats.jpeg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import org.apache.commons.imaging.ImageFormats;
import org.apache.commons.imaging.Imaging;
import org.junit.jupiter.api.Test;

public class JpegRoundtripTest {

    private void checkRoundtrip(BufferedImage src, JpegImagingParameters params) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new JpegImageParser().writeImage(src, baos, params);
        byte[] bytes = baos.toByteArray();

        
        BufferedImage dst = Imaging.getBufferedImage(bytes);
        assertNotNull(dst);
        assertEquals(src.getWidth(), dst.getWidth());
        assertEquals(src.getHeight(), dst.getHeight());
        
        // Due to JPEG lossy compression, we cannot expect exact pixel match, 
        // but we can check if they are roughly similar.
        // Or we just check a few pixels with a tolerance.
        int tolerance = 50; // High tolerance for JPEG
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int rgb1 = src.getRGB(x, y);
                int rgb2 = dst.getRGB(x, y);
                
                int r1 = (rgb1 >> 16) & 0xFF;
                int g1 = (rgb1 >> 8) & 0xFF;
                int b1 = rgb1 & 0xFF;
                
                int r2 = (rgb2 >> 16) & 0xFF;
                int g2 = (rgb2 >> 8) & 0xFF;
                int b2 = rgb2 & 0xFF;
                
                assertEquals(r1, r2, tolerance);
                assertEquals(g1, g2, tolerance);
                assertEquals(b1, b2, tolerance);
            }
        }
    }

    @Test
    public void testRoundtripRgb() throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setRGB(x, y, 0xFF0000 | (x * 16) << 8 | (y * 16));
            }
        }
        
        JpegImagingParameters params = new JpegImagingParameters();
        params.setQuality(100);
        checkRoundtrip(image, params);
    }

    @Test
    public void testRoundtripGrayscale() throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int gray = (x + y) * 8;
                image.setRGB(x, y, (gray << 16) | (gray << 8) | gray);
            }
        }
        
        JpegImagingParameters params = new JpegImagingParameters();
        params.setQuality(100);
        checkRoundtrip(image, params);
    }
}
