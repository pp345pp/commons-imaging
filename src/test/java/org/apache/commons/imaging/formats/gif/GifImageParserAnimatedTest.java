package org.apache.commons.imaging.formats.gif;

import org.apache.commons.imaging.Imaging;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class GifImageParserAnimatedTest {

    @Test
    public void testGetAllBufferedImages() throws Exception {
        File file = new File("src/test/resources/data/images/gif/animated/1/animated.gif");
        if (!file.exists()) {
            System.out.println("No animated gif found");
            return;
        }
        List<BufferedImage> images = Imaging.getAllBufferedImages(file);
        System.out.println("Frames: " + images.size());
        
        // The animated.gif has 2 frames.
        assertTrue(images.size() > 1);
        
        BufferedImage first = images.get(0);
        BufferedImage last = images.get(images.size() - 1);
        
        boolean hasDifference = false;
        for (int y = 0; y < first.getHeight(); y++) {
            for (int x = 0; x < first.getWidth(); x++) {
                if (first.getRGB(x, y) != last.getRGB(x, y)) {
                    hasDifference = true;
                    break;
                }
            }
        }
        assertTrue(hasDifference, "First and last frame should have different pixels");
    }
}
