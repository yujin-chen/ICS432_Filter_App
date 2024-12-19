package ics432.imgapp;

import com.jhlabs.image.InvertFilter;
import com.jhlabs.image.OilFilter;
import com.jhlabs.image.SolarizeFilter;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;

import static java.lang.System.currentTimeMillis;

public class JobThreads {


    static class ReaderThread implements Runnable {
        private final BoundedBuffer<WorkUnit> readBuffer;
        private final BoundedBuffer<WorkUnit> processBuffer;


        /**
         * Constructor
         *
         * @param readBuffer The read buffer
         * @param processBuffer The process buffer
         */
        public ReaderThread(BoundedBuffer<WorkUnit> readBuffer,BoundedBuffer<WorkUnit> processBuffer) {
            this.readBuffer = readBuffer;
            this.processBuffer = processBuffer;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    WorkUnit workUnit = readBuffer.take();
                    processBuffer.put(workUnit);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }


    public static class ProcessorThread implements Runnable {
        private final BoundedBuffer<WorkUnit> processBuffer;
        private final BoundedBuffer<WorkUnit> writeBuffer;
        private volatile boolean running = true; // Flag to control running state

        /**
         * Constructor
         *
         * @param processBuffer The process buffer
         * @param writeBuffer The write buffer
         */
        public ProcessorThread(BoundedBuffer<WorkUnit> processBuffer, BoundedBuffer<WorkUnit> writeBuffer) {
            this.processBuffer = processBuffer;
            this.writeBuffer = writeBuffer;
        }

        public void stop() {
            running = false;
        }

        @Override
        public void run() {
            try {
                while (running) {
                    WorkUnit workUnit = processBuffer.take();
                    double startProcessTime = currentTimeMillis() / 1000.0;
                    // Apply filter to the image
                    if(workUnit.getFilterName().equalsIgnoreCase("DPEdge") ||
                            workUnit.getFilterName().equalsIgnoreCase("DPFunk1") ||
                            workUnit.getFilterName().equalsIgnoreCase("DPFunk2"))
                    {
                        if (workUnit instanceof WorkUnitExternal) {
                            ((WorkUnitExternal) workUnit).processImage(); // Call process for WorkUnitExternal
                        }
                        writeBuffer.put(workUnit); // Pass to writer
                    }
                    else{
                        workUnit.setOutputImage(applyFilter(workUnit.getInputImage(), workUnit.getFilterName()));

                        writeBuffer.put(workUnit); // Pass to writer

                    }
                    workUnit.getTotalProcessAndWriteTime().incrementProcessTime(currentTimeMillis()/1000.0 - startProcessTime);

                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /**
         * Apply a filter to an image
         *
         * @param image The image to filter
         * @param filterName The name of the filter
         * @return The filtered image
         */
        private BufferedImage applyFilter(Image image, String filterName) {
            BufferedImageOp filter = createFilter(filterName);
            BufferedImage result = filter.filter(SwingFXUtils.fromFXImage(image, null), null);
            return result;
        }

        /**
         * Create a filter based on the filter name
         *
         * @param filterName The name of the filter
         * @return The filter
         */
        private BufferedImageOp createFilter(String filterName) {
            switch (filterName) {
                case "Invert":
                    return new InvertFilter();
                case "Solarize":
                    return new SolarizeFilter();
                case "Oil4":
                    OilFilter oil4Filter = new OilFilter();
                    oil4Filter.setRange(4);
                    return oil4Filter;
                case "Median":
                    return new MedianFilter();
                case "DPMedian":
                    return new DPMedianFilter();

                default:
                    throw new RuntimeException("Unknown filter " + filterName);
            }
        }

    }

    public static class WriterThread implements Runnable {
        private final BoundedBuffer<WorkUnit> writeBuffer;

        /**
         * Constructor
         *
         * @param writeBuffer The write buffer
         */
        public WriterThread(BoundedBuffer<WorkUnit> writeBuffer) {
            this.writeBuffer = writeBuffer;

        }

        @Override
        public void run() {
            try {
                while (true) {
                    WorkUnit workUnit = writeBuffer.take();
                    if(workUnit.getFilterName().equalsIgnoreCase("DPEdge") ||
                            workUnit.getFilterName().equalsIgnoreCase("DPFunk1") ||
                            workUnit.getFilterName().equalsIgnoreCase("DPFunk2"))
                    {
                        String outputPath = workUnit.getTargetDir() + FileSystems.getDefault().getSeparator() + workUnit.getFilterName() + "_" + workUnit.getInputFile().getFileName();
                        Path outputFilePath = Path.of(outputPath);
                        workUnit.getNumberOfImagesProcessed().increment();
                        workUnit.getFlwvp().addFiles(List.of(outputFilePath));
                        workUnit.getProgressBar().setProgress(( workUnit.getNumberOfImagesProcessed().getNumImageProcessed() / (double) workUnit.getTotalImagesInJob()));
                        ICS432ImgApp.statistics.newlyCompletedImageThread();
                    }else{
                        // Process the image and track the outcome
                        ImgTransformOutcome outcome = writeImageToDisk(
                                workUnit.getOutputImage(),
                                workUnit.getInputFile().getFileName().toString(),
                                workUnit.getFilterName(),
                                workUnit.getTargetDir().toString(),
                                workUnit.getFlwvp(),
                                workUnit.getTotalProcessAndWriteTime());

                        // Display or log the outcome (optional)
                        if (!outcome.success) {
                            System.err.println("Failed to write image: " + outcome.inputFile);
                            outcome.error.printStackTrace();
                        } else {
                            // ICS432ImgApp.statistics.newlyCompletedImageThread();
                            workUnit.getNumberOfImagesProcessed().increment();
                            workUnit.getProgressBar().setProgress(( workUnit.getNumberOfImagesProcessed().getNumImageProcessed() / (double) workUnit.getTotalImagesInJob()));
                            ICS432ImgApp.statistics.newlyCompletedImageThread();
                            System.out.println("Successfully wrote image: " + outcome.outputFile);
                        }

                    }

                    workUnit.cleanUp(); // Free resources


                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        /**
         * Write an image to disk
         *
         * @param image The image to write
         * @param fileName The name of the file to write
         * @param filterName The name of the filter
         * @param targetDir The target directory
         * @param flwvp The file list with viewport
         * @param totalProcessAndWriteTime The total process and write time
         * @return The outcome of the write operation
         */
        private ImgTransformOutcome writeImageToDisk(BufferedImage image, String fileName, String filterName, String targetDir, FileListWithViewPort flwvp, JobWindow.TotalProcessAndWriteTime totalProcessAndWriteTime) {
            double writeStartTime = currentTimeMillis() / 1000.0;
            String outputPath = targetDir + FileSystems.getDefault().getSeparator() + filterName + "_" + fileName;
            Path outputFilePath = Path.of(outputPath);

            try (OutputStream os = new FileOutputStream(outputPath);
                 ImageOutputStream outputStream = ImageIO.createImageOutputStream(os)) {
                     ImageIO.write(image, "jpg", outputStream);
                     flwvp.addFiles(List.of(outputFilePath));
                     ImgTransformOutcome result = new ImgTransformOutcome(true, Path.of(fileName), outputFilePath, null);
                     //Each Job has a total process and write time object
                     totalProcessAndWriteTime.incrementWriteTime(currentTimeMillis()/1000.0 - writeStartTime);

                     // Return success outcome
                     return result;

            } catch (IOException e) {
                // Return failure outcome in case of an error
                return new ImgTransformOutcome(false, Path.of(fileName), null, e);
            }
        }

    }


    /**
     * A helper nested class to define a imgTransform outcome for a given input file and ImgTransform
     */
    static class ImgTransformOutcome {

        // Whether the image transform is successful or not
        final boolean success;
        // The Input File path
        final Path inputFile;
        // The output file path (or null if failure)
        final Path outputFile;
        // The exception that was raised (or null if success)
        final Exception error;

        /**
         * Constructor
         *
         * @param success     Whether the imgTransform operation worked
         * @param input_file  The input file path
         * @param output_file The output file path  (null if success is false)
         * @param error       The exception raised (null if success is true)
         */
        ImgTransformOutcome(boolean success, Path input_file, Path output_file, Exception error) {
            this.success = success;
            this.inputFile = input_file;
            this.outputFile = output_file;
            this.error = error;
        }


    }



}
