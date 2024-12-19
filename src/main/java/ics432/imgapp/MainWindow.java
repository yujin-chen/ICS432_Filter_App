package ics432.imgapp;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import javafx.scene.control.Slider;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;






/**
 * A class that implements the "Main Window" for the app, which
 * allows the user to add input files for potential filtering and
 * to create an image filtering job
 */
class MainWindow {

    private final Stage primaryStage;
    private final Button quitButton;
    private Slider numProcessorSlider;
    private static Slider dataParallelThreadSlider;
    private int pendingJobCount = 0;
    private final FileListWithViewPort fileListWithViewPort;
    private int jobID = 0;
    private StatisticsWindow statisticsWindow;
    // List to store processor threads and their corresponding ProcessorThread runnables
    private final List<Thread> processorThreads = new ArrayList<>();
    // Atomic integer to manage the number of active processor threads
    private final AtomicInteger activeProcessorThreads = new AtomicInteger(0);
    private static final BoundedBuffer<WorkUnit> readBuffer = new BoundedBuffer<>(16); // Adjust size as needed
    private static final BoundedBuffer<WorkUnit> processBuffer = new BoundedBuffer<>(16); // Adjust size as needed
    private static final BoundedBuffer<WorkUnit> writeBuffer = new BoundedBuffer<>(16); // Adjust size as needed




    /**
     * Constructor
     *
     * @param primaryStage The primary stage
     * @param windowWidth  The window width
     * @param windowHeight The window height
     */
    MainWindow(Stage primaryStage, int windowWidth, int windowHeight) {

        double buttonPreferredHeight = 27.0;

        // Set up the primaryStage
        this.primaryStage = primaryStage;
        this.primaryStage.setTitle("ICS 432 Image Editing App");

        // Make this primaryStage non-closable
        this.primaryStage.setOnCloseRequest(Event::consume);

        // Create all widgets
        Button addFilesButton = new Button("Add Image Files");
        addFilesButton.setPrefHeight(buttonPreferredHeight);

        Button createJobButton = new Button("Create Job");
        createJobButton.setPrefHeight(buttonPreferredHeight);
        createJobButton.setDisable(true);

        quitButton = new Button("Quit");
        quitButton.setPrefHeight(buttonPreferredHeight);

        Button viewStatsButton = new Button("View Stats");
        viewStatsButton.setPrefHeight(buttonPreferredHeight);

        numProcessorSlider = new Slider(1, Runtime.getRuntime().availableProcessors(), 1);
        numProcessorSlider.setShowTickLabels(true);
        numProcessorSlider.setShowTickMarks(true);
        numProcessorSlider.setMajorTickUnit(1);
        numProcessorSlider.setMinorTickCount(0);
        numProcessorSlider.setBlockIncrement(1); // Move in increments of 1
        numProcessorSlider.setPrefWidth(300); // Make slider visually longer
        numProcessorSlider.setSnapToTicks(true);
        Label sliderValue = new Label("Number of Processors: " + (int) numProcessorSlider.getValue());

        dataParallelThreadSlider = new Slider(1, Runtime.getRuntime().availableProcessors(), 1);
        dataParallelThreadSlider.setShowTickLabels(true);
        dataParallelThreadSlider.setShowTickMarks(true);
        dataParallelThreadSlider.setMajorTickUnit(1);
        dataParallelThreadSlider.setMinorTickCount(0);
        dataParallelThreadSlider.setBlockIncrement(1); // Move in increments of 1
        dataParallelThreadSlider.setPrefWidth(300); // Make slider visually longer
        dataParallelThreadSlider.setSnapToTicks(true);
        Label dataParallelSliderValue = new Label("Number of Processors: " + (int) numProcessorSlider.getValue());


        this.fileListWithViewPort = new FileListWithViewPort(
                windowWidth * 0.98,
                windowHeight - 3 * buttonPreferredHeight - 3 * 5,
                true);

        // Listen for the "nothing is selected" property of the widget
        // to disable the createJobButton dynamically
        this.fileListWithViewPort.addNoSelectionListener(createJobButton::setDisable);

        // Set actions for all widgets
        addFilesButton.setOnAction(e -> addFiles(selectFilesWithChooser()));

        quitButton.setOnAction(e -> {
            // If the button is enabled, it's fine to quit
            this.primaryStage.close();

        });

        createJobButton.setOnAction(e -> {
            this.quitButton.setDisable(true);
            this.numProcessorSlider.setDisable(true);
            this.dataParallelThreadSlider.setDisable(true);
            this.pendingJobCount += 1;
            this.jobID += 1;

            JobWindow jw = new JobWindow(
                    (int) (windowWidth * 0.8), (int) (windowHeight * 0.8),
                    this.primaryStage.getX() + 100 + this.pendingJobCount * 10,
                    this.primaryStage.getY() + 50 + this.pendingJobCount * 10,
                    this.jobID, new ArrayList<>(this.fileListWithViewPort.getSelection()),
                    readBuffer);


            jw.addCloseListener(() -> {
                this.pendingJobCount -= 1;
                if (this.pendingJobCount == 0) {
                    this.quitButton.setDisable(false);
                    this.numProcessorSlider.setDisable(false);
                    this.dataParallelThreadSlider.setDisable(false);
                }
            });
        });

        viewStatsButton.setOnAction(e -> {

            viewStatsButton.setDisable(true);
            this.statisticsWindow = new StatisticsWindow(
                    350, 250,
                    this.primaryStage.getX() + 100 + this.pendingJobCount * 10,
                    this.primaryStage.getY() + 30 + this.pendingJobCount * 10);

            this.statisticsWindow.addCloseListener(() -> viewStatsButton.setDisable(false));
        });

        numProcessorSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            int roundedValue = (int) Math.round(newValue.doubleValue()); // Round to nearest multiple of 1
            numProcessorSlider.setValue(roundedValue);
            sliderValue.setText("Number of Processor: " + newValue.intValue());
            updateProcessorThreads(newValue.intValue());
        });

        dataParallelThreadSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            int roundedValue = (int) Math.round(newValue.doubleValue()); // Round to nearest multiple of 1
            dataParallelThreadSlider.setValue(roundedValue);
            dataParallelSliderValue.setText("Number of Data-Parallel Thread: " + newValue.intValue());
        });

        // Set default slider value to 1
        numProcessorSlider.setValue(1);
        dataParallelThreadSlider.setValue(1);

        // Display the default value
        sliderValue.setText("Number of Processor: 1");
        dataParallelSliderValue.setText("Number of Data-Parallel Thread: 1");

        // Initialize processor threads based on the initial slider value
        updateProcessorThreads(1);


        JobThreads.ReaderThread readerThread = new JobThreads.ReaderThread(readBuffer, processBuffer);
        JobThreads.WriterThread writerThread = new JobThreads.WriterThread(writeBuffer);

        // Create threads using these instances
        Thread reader = new Thread(readerThread);
        Thread writer = new Thread(writerThread);

        reader.setDaemon(true);
        writer.setDaemon(true);
        // Start threads
        reader.start();
        writer.start();

        //Construct the layout
        VBox layout = new VBox(5);

        layout.getChildren().add(addFilesButton);
        layout.getChildren().add(this.fileListWithViewPort);

        HBox row = new HBox(5);
        row.getChildren().add(createJobButton);
        row.getChildren().add(quitButton);
        row.getChildren().add(viewStatsButton);
        row.getChildren().addAll(numProcessorSlider, sliderValue);
        row.getChildren().addAll(dataParallelThreadSlider, dataParallelSliderValue);
        layout.getChildren().add(row);

        Scene scene = new Scene(layout, windowWidth, windowHeight);
        this.primaryStage.setScene(scene);
        this.primaryStage.setResizable(false);

        // Make this primaryStage non-closable
        this.primaryStage.setOnCloseRequest(Event::consume);

        //  Show it on  screen.
        this.primaryStage.show();


    }


    /**
     * Method that pops up a file chooser and returns chosen image files
     *
     * @return The list of files
     */
    private List<Path> selectFilesWithChooser() {

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Choose Image Files");
        fileChooser.getExtensionFilters().addAll(
                new ExtensionFilter("Jpeg Image Files", "*.jpg", "*.jpeg", "*.JPG", "*.JPEG"));
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(this.primaryStage);

        if (selectedFiles == null) {
            return new ArrayList<>();
        } else {
            return selectedFiles.stream().collect(ArrayList::new,
                    (c, e) -> c.add(Paths.get(e.getAbsolutePath())),
                    ArrayList::addAll);
        }
    }

    /**
     * Method that adds files to the list of known files
     *
     * @param files The list of files
     */
    private void addFiles(List<Path> files) {

        if (files != null) {
            this.fileListWithViewPort.addFiles(files);
        }
    }

    public void updateProcessorThreads(int newThreadCount) {
        int currentThreadCount = activeProcessorThreads.get();

        // Increase threads if the new count is higher
        if (newThreadCount > currentThreadCount) {
            for (int i = currentThreadCount; i < newThreadCount; i++) {
                JobThreads.ProcessorThread processorRunnable = new JobThreads.ProcessorThread(processBuffer, writeBuffer);
                Thread processorThread = new Thread(processorRunnable);
                processorThread.setDaemon(true);
                processorThread.start();

                processorThreads.add(processorThread);
                activeProcessorThreads.incrementAndGet();
            }
            System.err.println("Number of Processor: " + processorThreads.size());
        }
        // Decrease threads if the new count is lower
        else if (newThreadCount < currentThreadCount) {
            for (int i = currentThreadCount; i > newThreadCount; i--) {
                Thread threadToStop = processorThreads.remove(processorThreads.size() - 1);
                threadToStop.interrupt();
                activeProcessorThreads.decrementAndGet();
            }
            System.err.println("Number of Processor: " + processorThreads.size());
        }
    }

    public static int getDPThreadCount() {
        return (int) dataParallelThreadSlider.getValue();
    }



}