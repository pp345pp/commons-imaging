package org.apache.commons.imaging;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.ImagingParameters;

/**
 * Interface for image writers.
 *
 * @param <T> the type of imaging parameters
 */
public interface AbstractImageWriter<T extends ImagingParameters<T>> {

    /**
     * Writes the image to the output stream.
     *
     * @param src    the source image
     * @param os     the output stream
     * @param params the parameters
     * @throws ImagingException if an imaging error occurs
     * @throws IOException      if an I/O error occurs
     */
    void writeImage(BufferedImage src, OutputStream os, T params) throws ImagingException, IOException;
}
