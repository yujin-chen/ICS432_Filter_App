package ics432.imgapp;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * A helper class that implements a widget that shows
 * an image file list on the left-hand side, and the actual
 * image that correspond to the selected file on the right-hand side (in a "viewport").
 * The user can navigate through the entries using the mouse or the keyboard's
 * arrow keys. Entries can be selected using shift, and ^a selects all entries.
 * If the widget is set to be "editable", then entries can be
 * removed by the user by using the backspace key. This class extends HBox.
 */
class FileListWithViewPort extends HBox {

    private final ObservableList<Path> availableFiles;
    private final ListView<Path> availableFilesView;
    private final ImageView iv;
    private final double height;
    private final double width;
    private final Image emptyImage;
    private final Image brokenImage;
    private final SimpleBooleanProperty nothingIsSelected;
    private final boolean isEditable;

    /**
     * Constructor
     *
     * @param width      The widget's width in pixels
     * @param height     The widget's height in pixels
     * @param isEditable Whether the widget is editable
     */
    FileListWithViewPort(double width, double height,
                         boolean isEditable) {

        // Set the vertical spacing between items added to the widget
        this.setSpacing(5);

        // Set the max geometry of the display
        this.setMaxHeight(height);
        this.setMaxWidth(width);

        // Which fraction of the width is the list (the other being the viewport)
        double listFraction = 0.4;

        // Set instance variables based on constructor parameters
        this.height = height;
        this.width = width;
        this.isEditable = isEditable;

        // Create the boolean that  are "observable", i.e., listeners can be added
        this.nothingIsSelected = new SimpleBooleanProperty(true);

        // Get references to the empty and broken images (in the resources directory)
        this.emptyImage = Util.loadImageFromResourceFile("empty-image.png");
        this.brokenImage = Util.loadImageFromResourceFile("broken-image.png");

        // Create the left-hand side file list (a ListView of an observable list of Path objects)
        this.availableFiles = FXCollections.observableArrayList();
        this.availableFilesView = new ListView<>(availableFiles);
        // Set what is displayed in the ListView for each entry, if any
        this.availableFilesView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                Platform.runLater(() -> {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null); // nothing
                    } else {
                        setText(item.toAbsolutePath().toString());  // the file path
                    }
                });
            }
        });

        // Allow for multiple selections
        this.availableFilesView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        // Set geometry (a bit paranoid)
        this.availableFilesView.setPrefWidth(width * listFraction);
        this.availableFilesView.setMinWidth(width * listFraction);
        this.availableFilesView.setMaxWidth(width * listFraction);
        this.availableFilesView.setPrefHeight(height);
        this.availableFilesView.setMaxHeight(height);

        // Create an image viewport as an ImageView widget
        this.iv = new ImageView();
        this.iv.setPreserveRatio(true);  // make aspect ratio of images preserved
        displayInViewPort(emptyImage); // initialize it to display the "empty" image

        // Set viewport's behavior when list item is clicked
        this.availableFilesView.setOnMouseClicked(e -> Platform.runLater(() -> {
            // Display the selected image
            displayInViewPort(this.availableFilesView.getSelectionModel().getSelectedItem());
            // Set the observable "nothing is selected" boolean to what it should be
            this.nothingIsSelected.setValue(this.availableFilesView.getSelectionModel().getSelectedItems().isEmpty());

        }));

        // Set viewport's behavior when user uses the keyboard
        this.availableFilesView.setOnKeyPressed(e -> {

            // If the key pressed was BACK_SPACE and the list is editable, remove items
            if (this.isEditable && (e.getCode() == KeyCode.BACK_SPACE)) {

                int to_select_after = Math.max(0, this.availableFilesView.getSelectionModel().getSelectedIndices().getFirst() - 1);
                this.availableFiles.removeAll(this.availableFilesView.getSelectionModel().getSelectedItems());
                if (!this.availableFiles.isEmpty()) {
                    this.availableFilesView.getSelectionModel().select(to_select_after);
                }
            }

            // If the key pressed was UP, DOWN, or BACKSPACE, update the displayed image
            if (e.getCode() == KeyCode.UP || e.getCode() == KeyCode.DOWN | e.getCode() == KeyCode.BACK_SPACE) {
                Platform.runLater(() -> displayInViewPort(this.availableFilesView.getSelectionModel().getSelectedItem()));
            }

            //  Whatever key was selected, update the "nothing selected" boolean
            this.nothingIsSelected.setValue(this.availableFilesView.getSelectionModel().getSelectedItems().isEmpty());

        });

        // Add all sub-widgets to the main widget
        this.getChildren().add(this.availableFilesView);
        this.getChildren().add(this.iv);
    }

    /**
     * Method to register a change listener for "no selection" changes.
     *
     * @param listener The listener method
     */
    public void addNoSelectionListener(Consumer<Boolean> listener) {
        // Pass to the SimpleBooleanProperty a (more fancy)
        // listener that calls the (less fancy) user-provided listener
        this.nothingIsSelected.addListener((observable, oldValue, newValue) ->
                listener.accept(newValue));
    }

    /**
     * Method to clear all files from the list
     */
    public void clear() {
        // Must be done in the JavaFX application thread, and this method
        // may be called from any thread, so let's handle two cases (we don't
        // want the JavaFX Application thread to call runLater)
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::clearFileList);
        } else {
            this.clearFileList();
        }
    }

    /**
     * Method to clear the file list (should always run in the JavaFX Application thread)
     */
    private void clearFileList() {
        this.availableFiles.clear();
        this.nothingIsSelected.setValue(true);
        this.displayInViewPort((Path) null);
    }

    /**
     * Method that returns the number of files in the ListView
     *
     * @return the number of files
     */
    public int getNumFiles() {
        return this.availableFiles.size();
    }

    /**
     * Method to add file paths to the ListView
     *
     * @param toAdd List of Path objects
     */
    public void addFiles(List<Path> toAdd) {

        // If null is passed in, do nothing
        if (toAdd == null) return;

        // Add the file paths to the file list
        for (Path f : toAdd) {
            // Determine if this is a new file path
            boolean add = true;
            for (Path g : availableFiles) {
                if (f.equals(g)) {
                    add = false;
                    break;
                }
            }
            // If was a new path, then add it.
            if (add) {
                this.availableFiles.add(f);
            }
        }

        Platform.runLater(() -> this.nothingIsSelected.setValue(this.availableFilesView.getSelectionModel().getSelectedItems().isEmpty()));
    }

    /**
     * Retrieve the list of file paths selected by the user
     *
     * @return The list of selected file paths
     */
    public List<Path> getSelection() {
        return this.availableFilesView.getSelectionModel().getSelectedItems();
    }

    /**
     * Helper method to display and image file in the viewport.
     *
     * @param file The image file to display in the viewport. If null is passed, then
     *             the empty image will be displayed. If an invalid path is passed, then
     *             the broken image will be displayed.
     */
    private void displayInViewPort(Path file) {

        // Initialize the image display to the broken image in case the path is invalid
        Image img = brokenImage;

        if (file != null) {
            try {
                img = new Image(file.toUri().toURL().toString());
            } catch (MalformedURLException ignore) {
            }
            if (img.isError()) {
                img = brokenImage;
            }
        } else {
            img = emptyImage;
        }
        // Display the image obtained from the file
        displayInViewPort(img);
    }

    /**
     * Helper method to display an image in the viewport
     *
     * @param img The image to display in the viewport
     */
    private void displayInViewPort(Image img) {

        // Display the image in the ImageView
        this.iv.setImage(img);

        // Max out the width
        double image_ratio = img.getHeight() / img.getWidth();
        double target_width = 2 * this.width / 3;

        // Reduce it if necessary for height
        if (target_width * image_ratio > this.height) {
            target_width = this.height / image_ratio;
        }

        // Set the viewport's fit width
        iv.setFitWidth(target_width);
    }
}
