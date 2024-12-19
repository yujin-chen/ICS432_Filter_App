package ics432.imgapp;

import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ColorModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.*;

public class DPMedianFilter implements BufferedImageOp {
    private final int numThreads;

    public DPMedianFilter() {
        this.numThreads = MainWindow.getDPThreadCount();
    }

    private boolean inBounds(int x, int y, int width, int height) {
        return (x >= 0 && x < width && y >= 0 && y < height);
    }

    protected int processPixel(BufferedImage image, int x, int y) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] newBytes = new byte[3];

        for (int channel = 0; channel < 3; channel++) {
            ArrayList<Byte> neighbors = new ArrayList<>();

            for (int nx = x - 1; nx <= x + 1; nx++) {
                for (int ny = y - 1; ny <= y + 1; ny++) {
                    if (inBounds(nx, ny, width, height)) {
                        neighbors.add(RGB.intToBytes(image.getRGB(nx, ny))[channel]);
                    }
                }
            }

            Collections.sort(neighbors);
            newBytes[channel] = neighbors.get(neighbors.size() / 2);
        }

        return RGB.bytesToInt(newBytes);
    }

    @Override
    public BufferedImage filter(BufferedImage src, BufferedImage dest) {
        int width = src.getWidth();
        int height = src.getHeight();

        dest = new BufferedImage(width, height, src.getType());
        CyclicBarrier barrier = new CyclicBarrier(numThreads);

        int rowsPerThread = height / numThreads;

        Thread[] threads = new Thread[numThreads];

        for (int threadIdx = 0; threadIdx < numThreads; threadIdx++) {
            final int startRow = threadIdx * rowsPerThread;
            final int endRow = (threadIdx == numThreads - 1) ? height : (startRow + rowsPerThread);

            // Lambda expression uses effectively final startRow and endRow
            BufferedImage finalDest = dest;
            threads[threadIdx]= new Thread(() -> {
                for (int i = startRow; i < endRow; i++) {
                    for (int j = 0; j < width; j++) {
                        finalDest.setRGB(j, i, processPixel(src, j, i));
                    }
                }

                try {
                    barrier.await();
                } catch (InterruptedException | BrokenBarrierException e) {
                    Thread.currentThread().interrupt();
                }
            });

            threads[threadIdx].start();
        }


        // Join all threads to ensure completion
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return dest;
    }

    // Additional methods for BufferedImageOp
    @Override
    public java.awt.geom.Rectangle2D getBounds2D(BufferedImage src) { return null; }
    @Override
    public BufferedImage createCompatibleDestImage(BufferedImage src, ColorModel destCM) { return null; }
    @Override
    public java.awt.geom.Point2D getPoint2D(java.awt.geom.Point2D srcPt, java.awt.geom.Point2D dstPt) { return null; }
    @Override
    public java.awt.RenderingHints getRenderingHints() { return null; }
}
