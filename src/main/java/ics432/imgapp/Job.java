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
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import static javax.imageio.ImageIO.createImageOutputStream;

/**
 * A class that defines the "job" abstraction, that is, a  set of input image files
 * to which a filter must be applied, thus generating a set of output
 * image files. Each output file name is the input file name prepended with
 * the ImgTransform name and an underscore.
 */
class Job {

    private final String filterName;
    private final Path targetDir;
    private final Iterator<Path> inputFiles;
    private final Consumer<ImgTransformOutcome> outcome;

    private double totalReadTime = 0.0;
    private double totalWriteTime = 0.0;
    private double totalProcessTime = 0.0;
    private double totalJobTime = 0.0;
    private double totalInputSize = 0.0;
    private double currentImageExecutionTime = 0.0;

    private volatile boolean canceled = false;


    /**
     * Constructor
     *
     * @param filterName The name of the filter to apply to input images
     * @param targetDir  The target directory in which to generate output images
     * @param inputFiles The list of input file paths
     * @param outcome    The outcome consumer
     */
    Job(String filterName, Path targetDir, List<Path> inputFiles, Consumer<ImgTransformOutcome> outcome) {
        this.filterName = filterName;
        this.targetDir = targetDir;
        this.inputFiles = inputFiles.iterator();
        this.outcome = outcome;

    }

    /**
     * Method to process the next image
     * @return true if there are more images to process, false otherwise
     * @throws IOException If there is an error processing the image
     */
    boolean processNextImage() throws IOException {
        if (canceled || !inputFiles.hasNext()) {
            return false; // No more images to process
        }

        Path inputFile = inputFiles.next();
        System.err.println("Applying " + this.filterName + " to " + inputFile.toAbsolutePath() + " ...");
        Path outputFile = processInputFile(inputFile);

        // Update statistics upon successful processing
        // Calculate input size (in MB) and execution time (in seconds)
        double inputSize = inputFile.toFile().length()/1024.0/1024.0 ;


        // Update total input size that displayed in the Job window
        totalInputSize += inputSize ;
        double executionTime = currentImageExecutionTime;

        outcome.accept(new ImgTransformOutcome(true, inputFile, outputFile, null));
        // Update the statistics
        ICS432ImgApp.statistics.newlyProcessedImage(filterName, inputSize, executionTime);
        return true;
    }

    /**
     * Method to process the input file
     *
     * @param inputFile The input file path
     * @return The output file
     * @throws IOException If there is an error processing the file
     */
    private Path processInputFile(Path inputFile) throws IOException {
        //start the job timer
        long jobStartTime = System.currentTimeMillis();
        //start the input timer
        long readStartTime = System.currentTimeMillis();


        // Load the image from file
        Image image;
        try {
            image = new Image(inputFile.toUri().toURL().toString());
            if (image.isError()) {
                throw new IOException("Error while reading from " + inputFile.toAbsolutePath() +
                        " (" + image.getException().toString() + ")");
            }
        } catch (IOException e) {
            throw new IOException("Error while reading from " + inputFile.toAbsolutePath());
        }
        //end the input timer and compute the total read time
        long readEndTime = System.currentTimeMillis();
        totalReadTime += ((readEndTime - readStartTime)/1000.0);

        // Create the filter
        BufferedImageOp filter = createFilter(filterName);

        // Process the image
        //start the process timer
        long processStartTime = System.currentTimeMillis();
        BufferedImage img = filter.filter(SwingFXUtils.fromFXImage(image, null), null);
        //end the process timer and compute the total process time
        long processEndTime = System.currentTimeMillis();
        totalProcessTime += ((processEndTime - processStartTime)/1000.0);

        // Write the image back to a file
        //start the write or output timer
        long writeStartTime = System.currentTimeMillis();
        String outputPath = this.targetDir + FileSystems.getDefault().getSeparator() + this.filterName + "_" + inputFile.getFileName();
        try {
            OutputStream os = new FileOutputStream(outputPath);
            ImageOutputStream outputStream = createImageOutputStream(os);
            ImageIO.write(img, "jpg", outputStream);
        } catch (IOException | NullPointerException e) {
            throw new IOException("Error while writing to " + outputPath);
        }
        //end the write or output timer and compute the total write or output time
        long writeEndTime = System.currentTimeMillis();
        totalWriteTime += ((writeEndTime - writeStartTime)/1000.0);

        //end the job timer and compute the total job time
        long jobEndTime = System.currentTimeMillis();
        totalJobTime += (jobEndTime - jobStartTime)/1000.0;
        currentImageExecutionTime = (jobEndTime - jobStartTime)/1000.0;

        // Success!
        return Paths.get(outputPath);
    }

    /**
     * A helper method to create a Filter object
     *
     * @param filterName the filter's name
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
            default:
                throw new RuntimeException("Unknown filter " + filterName);
        }
    }


    /**
     * Getter methods
     */
    double getTotalReadTime() {
        System.err.println("Total Read Time: " + totalReadTime);
        return totalReadTime;
    }

    double getTotalWriteTime() {
        System.err.println("Total Write Time: " + totalWriteTime);
        return totalWriteTime;
    }

    double getTotalProcessTime() {
        System.err.println("Total Process Time: " + totalProcessTime);
        return totalProcessTime;
    }

    double getTotalJobTime() {
        System.err.println("Total Job Time: " + totalJobTime);
        return totalJobTime;
    }

    double getTotalInputSize() {
        return totalInputSize;
    }

    // cancel listener
    public void cancel() {
        this.canceled = true;
    }

    // check if the job is canceled
    public boolean isCanceled() {
        return canceled;
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