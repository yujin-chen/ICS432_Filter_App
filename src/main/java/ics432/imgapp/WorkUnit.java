package ics432.imgapp;

import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

/**
 * A class that represents a work unit, which is a unit of work that
 * can be processed by worker threads(reader, processor,and writer).
 * A work unit is either a regular work unit that contains an input image
 * to be processed, or a stop signal that indicates that the worker thread
 * should stop processing work units.
 */
public class WorkUnit {
    private Path inputFile;
    private Image inputImage; // This should be loaded in the constructor
    private BufferedImage outputImage;
    private final Path targetDir;
    private final String filterName;
    private final FileListWithViewPort flwvp;
    private final ProgressBar progressBar;
    private final JobWindow.NumProcessCounter numberOfImagesProcessed;
    private final int totalImagesInJob;
    private final JobWindow.TotalProcessAndWriteTime totalProcessAndWriteTime;

    /**
     * Constructor
     *
     * @param inputFile The input file
     * @param targetDir The target directory
     * @param filterName The filter name
     * @param flwvp The file list with view port
     * @param progressBar The progress bar
     * @param numProcessCounter The number of images processed
     * @param totalImagesInJob The total number of images in the job
     * @param totalProcessAndWriteTime The total process and write time
     */
    public WorkUnit(Path inputFile, Path targetDir, String filterName, FileListWithViewPort flwvp, ProgressBar progressBar, JobWindow.NumProcessCounter numProcessCounter, int totalImagesInJob, JobWindow.TotalProcessAndWriteTime totalProcessAndWriteTime) {
        this.inputFile = inputFile;
        this.inputImage = loadInputImage(); // Load the image once upon creation
        this.targetDir = targetDir;
        this.filterName = filterName;
        this.flwvp = flwvp;
        this.progressBar = progressBar;
        this.numberOfImagesProcessed = numProcessCounter;
        this.totalImagesInJob = totalImagesInJob;
        this.totalProcessAndWriteTime = totalProcessAndWriteTime;
    }



    //This method load the input image
    private Image loadInputImage() {
        try {
            return new Image(inputFile.toUri().toURL().toString());
        } catch (Exception e) {
            System.err.println("Error loading image from " + inputFile + ": " + e.getMessage());
            return null; // Handle error case
        }
    }

    //getter method to retrieve the image
    public Image getInputImage() {
        return inputImage;
    }

    //getter method to retrieve the target directory
    public Path getTargetDir() {
        return targetDir;
    }

    //getter method to retrieve the filter name
    public String getFilterName() {
        return filterName;
    }

    //getter method to retrieve the file list with view port
    public FileListWithViewPort getFlwvp() {
        return flwvp;
    }

    //getter method to retrieve the progress bar
    public ProgressBar getProgressBar() {
        return progressBar;
    }

    //getter method to retrieve the total number of images in the job
    public int getTotalImagesInJob() {
        return totalImagesInJob;
    }

    //getter method to retrieve the Object of NumProcessCounter
    public JobWindow.NumProcessCounter getNumberOfImagesProcessed() {
        return numberOfImagesProcessed;
    }

    //getter method to retrieve Object of TotalProcessAndWriteTime
    public JobWindow.TotalProcessAndWriteTime getTotalProcessAndWriteTime() {
        return totalProcessAndWriteTime;
    }



    /**
     * Method to set the outputImage of this workunit object to the argument which is the
     * processed image.
     * @param outputImage processed image
     *
     */
    public void setOutputImage(BufferedImage outputImage) {
        this.outputImage = outputImage;
    }

    //getter method to retrieve the output image
    public BufferedImage getOutputImage() {
        return outputImage;
    }

    //A getter method to retrieve path of the input file
    public Path getInputFile() {
        return inputFile;
    }

    //A method to clean up the resources
    public void cleanUp() {
        // Free any resources if needed
        inputFile = null; // Clear input file to free memory
        inputImage = null; // Clear input image to free memory
        outputImage = null; // Clear output image to free memory
    }
}
