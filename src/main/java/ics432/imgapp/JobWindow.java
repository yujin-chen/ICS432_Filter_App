package ics432.imgapp;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.Event;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;


/**
 * A class that implements a "Job Window" on which a user
 * can launch a Job
 */
class JobWindow extends Stage {

    private Path targetDir;
    private final List<Path> inputFiles;
    private final FileListWithViewPort flwvp;
    private final Button changeDirButton;
    private final TextField targetDirTextField;
    private final Button runButton;
    private final Button closeButton;
    private final ComboBox<String> imgTransformList;
    private final Label timeLabel;
    private final Button cancelButton;

    private Thread jobThread;
    private Job job;
    private final ProgressBar progressBar;
    private final BoundedBuffer<WorkUnit> readBuffer;

    private int numberOfImages = 0;
    private double finalTotalJobStartTime = 0.0;
    private double finalTotalJobTime = 0.0;
    private double readStartTime = 0.0;
    private double totalReadTime = 0.0;
    private double totalInputSize =0.0;


    /**
     * Constructor
     *
     * @param windowWidth          The window's width
     * @param windowHeight         The window's height
     * @param X                    The horizontal position of the job window
     * @param Y                    The vertical position of the job window
     * @param id                   The id of the job
     * @param inputFiles           The batch of input image files
     */

    JobWindow(int windowWidth, int windowHeight, double X, double Y, int id, List<Path> inputFiles, BoundedBuffer<WorkUnit> readBuffer) {

        // The  preferred height of buttons
        double buttonPreferredHeight = 27.0;

        // Set up instance variables
        targetDir = Paths.get(inputFiles.getFirst().getParent().toString()); // Same dir as input images
        this.inputFiles = inputFiles;
        this.readBuffer = readBuffer;


        // Set up the window
        this.setX(X);
        this.setY(Y);
        this.setTitle("Image Transformation Job #" + id);
        this.setResizable(false);

        // Make this window non-closable
        this.setOnCloseRequest(Event::consume);

        // Create all sub-widgets in the window
        Label targetDirLabel = new Label("Target Directory:");
        targetDirLabel.setPrefWidth(115);

        // Create a "change target directory"  button
        this.changeDirButton = new Button("");
        this.changeDirButton.setId("changeDirButton");
        this.changeDirButton.setPrefHeight(buttonPreferredHeight);
        Image image = Util.loadImageFromResourceFile("folder-icon.png");
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(10);
        imageView.setFitHeight(10);
        this.changeDirButton.setGraphic(imageView);


        // Create a "target directory"  text field
        this.targetDirTextField = new TextField(this.targetDir.toString());
        this.targetDirTextField.setDisable(true);
        HBox.setHgrow(targetDirTextField, Priority.ALWAYS);

        // Create an informative label
        Label transformLabel = new Label("Transformation: ");
        transformLabel.setPrefWidth(115);

        //  Create the pull-down list of image transforms
        this.imgTransformList = new ComboBox<>();
        this.imgTransformList.setId("imgTransformList");  // For TestFX
        this.imgTransformList.setItems(FXCollections.observableArrayList(
                "Invert",
                "Solarize",
                "Oil4",
                "Median",
                "DPMedian",
                "DPEdge",
                "DPFunk1",
                "DPFunk2"
        ));
        this.imgTransformList.getSelectionModel().selectFirst(); //Chooses first imgTransform as default

        // Create a "Run" button
        this.runButton = new Button("Run job (on " + inputFiles.size() + " image" + (inputFiles.size() == 1 ? "" : "s") + ")");
        this.runButton.setId("runJobButton");
        this.runButton.setPrefHeight(buttonPreferredHeight);

        //Create a "cancel" button
        this.cancelButton = new Button("Cancel");
        this.cancelButton.setId("cancelButton");
        this.cancelButton.setPrefHeight(buttonPreferredHeight);

        // Create the FileListWithViewPort display
        this.flwvp = new FileListWithViewPort(windowWidth * 0.98, windowHeight - 4 * buttonPreferredHeight - 3 * 5, false);
        this.flwvp.addFiles(inputFiles);

        // Create a "Close" button
        this.closeButton = new Button("Close");
        this.closeButton.setId("closeButton");
        this.closeButton.setPrefHeight(buttonPreferredHeight);

        // Create a label for time statistics
        this.timeLabel = new Label("No time, not yet executed");
        this.timeLabel.setAlignment(Pos.CENTER_RIGHT);


        // Set actions for all widgets
        this.changeDirButton.setOnAction(e -> {
            DirectoryChooser dirChooser = new DirectoryChooser();
            dirChooser.setTitle("Choose target directory");
            File dir = dirChooser.showDialog(this);
            this.setTargetDir(Paths.get(dir.getAbsolutePath()));
        });

        this.runButton.setOnAction(e -> {
            this.cancelButton.setDisable(false);
            this.timeLabel.setText("Running job...");
            executeJob(imgTransformList.getSelectionModel().getSelectedItem());
        });

        this.closeButton.setOnAction(f -> this.close());

        this.cancelButton.setOnAction(e -> {
            if (jobThread != null && jobThread.isAlive()) {
                job.cancel(); // Set the cancel flag
                this.cancelButton.setDisable(true); // Disable cancel button after click
            }

        });

        // Create a progress bar
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(windowWidth * 0.7);  // Set the width to 95% of the window width
        progressBar.setPrefHeight(20);  // Set a specific height for the progress bar
        progressBar.setVisible(false);  // Initially hidden


        // Build the scene
        VBox layout = new VBox(5);

        HBox row1 = new HBox(5);
        row1.setAlignment(Pos.CENTER_LEFT);
        row1.getChildren().add(targetDirLabel);
        row1.getChildren().add(changeDirButton);
        row1.getChildren().add(targetDirTextField);
        layout.getChildren().add(row1);

        // Create an HBox to hold both the imgTransformList and the ProgressBar side by side
        HBox row2 = new HBox(10);  // Horizontal box with spacing of 10 between items
        row2.setAlignment(Pos.CENTER_LEFT);  // Align to the left
        // Add imgTransformList and progressBar to the HBox
        row2.getChildren().addAll(transformLabel, imgTransformList, progressBar);
        // Add the HBox to the main layout (VBox)
        layout.getChildren().add(row2);  // Add the HBox to the main VBox layout

        layout.getChildren().add(flwvp);

        HBox row3 = new HBox(5);
        row3.getChildren().add(runButton);
        row3.getChildren().add(closeButton);
        row3.getChildren().add(cancelButton);
        this.cancelButton.setDisable(true);
        layout.getChildren().add(row3);

        layout.getChildren().add(timeLabel);

        Scene scene = new Scene(layout, windowWidth, windowHeight);
        // Pop up the new window
        this.setScene(scene);
        this.toFront();
        this.show();
    }

    /**
     * Method to add a listener for the "window was closed" event
     *
     * @param listener The listener method
     */
    public void addCloseListener(Runnable listener) {

        this.addEventHandler(WindowEvent.WINDOW_HIDDEN, (event) -> listener.run());
    }

    /**
     * Method to set the target directory
     *
     * @param dir A directory
     */
    private void setTargetDir(Path dir) {
        if (dir != null) {
            this.targetDir = dir;
            this.targetDirTextField.setText(targetDir.toAbsolutePath().toString());
        }
    }

    public class NumProcessCounter {
        private int numImageProcessed;

        public NumProcessCounter() {
            this.numImageProcessed = 0;
        }

        // Synchronized increment to ensure thread safety
        public synchronized void increment() {
            this.numImageProcessed++;
            notifyAll(); // Notify any waiting threads
        }

        public int getNumImageProcessed() {
            return numImageProcessed;
        }
    }

    public class TotalProcessAndWriteTime {
        private double processTime;
        private double writeTime;

        public TotalProcessAndWriteTime() {
            this.processTime = 0.0;
            this.writeTime = 0.0;
        }

        // Synchronized increment to ensure thread safety
        public synchronized void incrementProcessTime(double time) {
            this.processTime += time;
        }

        public synchronized void incrementWriteTime(double time) {
            this.writeTime += time;
        }

        public double getProcessTime() {
            return processTime;
        }

        public double getWriteTime() {
            return writeTime;
        }
    }

    /**
     * A method to execute the job
     *
     * @param filterName The name of the filter to apply to input images
     */
    private void executeJob(String filterName) {
        // Clear the display
        numberOfImages = flwvp.getNumFiles();
        flwvp.clear();

        progressBar.setVisible(true);  // Show the progress bar
        progressBar.setProgress(0);    // Reset progress to

        //Check if a job is already running
        if (jobThread != null && jobThread.isAlive()) {
            // A job is already running
            return;
        }

        this.closeButton.setDisable(true);
        this.changeDirButton.setDisable(true);
        this.runButton.setDisable(true);
        this.imgTransformList.setDisable(true);

        // Create a thread to run the job
        jobThread = new Thread(() -> {
            finalTotalJobStartTime = System.currentTimeMillis()/1000.0;

            NumProcessCounter numProcessCounter = new NumProcessCounter();
            TotalProcessAndWriteTime totalProcessAndWriteTime = new TotalProcessAndWriteTime();
            try{
                readStartTime = System.currentTimeMillis()/1000.0;
                System.err.println(numberOfImages);
                totalInputSize = 0.0;
                for (Path path : inputFiles) {
                    if (filterName.equalsIgnoreCase("DPEdge") ||
                            filterName.equalsIgnoreCase("DPFunk1") ||
                            filterName.equalsIgnoreCase("DPFunk2")) {
                        WorkUnit workUnit = new WorkUnitExternal(
                                path,
                                targetDir,
                                filterName,
                                flwvp,
                                progressBar,
                                numProcessCounter,
                                numberOfImages,
                                totalProcessAndWriteTime);
                        readBuffer.put(workUnit);
                        totalInputSize += (path.toFile().length()) / 1024.0 / 1024.0;
                    }else{
                        WorkUnit workUnit = new WorkUnit(
                                path,
                                targetDir,
                                filterName,
                                flwvp,
                                progressBar,
                                numProcessCounter,
                                numberOfImages,
                                totalProcessAndWriteTime);
                        readBuffer.put(workUnit);
                        totalInputSize += (path.toFile().length()) / 1024.0 / 1024.0;

                    }
                }
                totalReadTime = System.currentTimeMillis()/1000.0 - readStartTime;

                // Wait until all images are processed
                synchronized (numProcessCounter) {
                    while (numProcessCounter.getNumImageProcessed() != numberOfImages) {
                        System.err.println("num image process: " + numProcessCounter.getNumImageProcessed());
                        numProcessCounter.wait(); // Wait until notified
                    }
                }
                finalTotalJobTime = System.currentTimeMillis()/1000.0 - finalTotalJobStartTime;

                System.err.println("image done: " + numProcessCounter.getNumImageProcessed());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally{
                Platform.runLater(() -> {
                    // Use the instance-level time methods from each thread
                    timeLabel.setText(String.format(
                            "Job took %.2f seconds to run.  Read: %.2f seconds  Write: %.2f seconds  Process: %.2f seconds Input size: %.2f MB",
                            finalTotalJobTime,
                            totalReadTime,
                            totalProcessAndWriteTime.getWriteTime(),
                            totalProcessAndWriteTime.getProcessTime(),
                            totalInputSize));

                    // Increment statistics after successful job
                    ICS432ImgApp.statistics.newlyCompletedJob();
                    ICS432ImgApp.statistics.newlyProcessedJobThread( filterName, totalInputSize, finalTotalJobTime);

                    this.closeButton.setDisable(false);
                    this.changeDirButton.setDisable(false);
                    this.runButton.setDisable(false);
                    this.imgTransformList.setDisable(false);
                    this.cancelButton.setDisable(true);
                    progressBar.setVisible(false);
                });
            }


        });
        // Start the job thread
        jobThread.start();
    }

}
