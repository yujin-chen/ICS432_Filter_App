package ics432.imgapp;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * A class that implements a "Statistics Window" that displays
 * useful information
 */

class StatisticsWindow extends Stage {

    /**
     * Constructor
     *
     * @param windowWidth      The window's width
     * @param windowHeight     The window's height
     * @param X          The horizontal position of the job window
     * @param Y          The vertical position of the job window
     */
    StatisticsWindow(int windowWidth, int windowHeight, double X, double Y) {

        double buttonPreferredHeight = 27.0;
        double separatorPreferredHeight = 5;
        double separatorPreferredWidth = 5;

        // Set up the window
        this.setX(X);
        this.setY(Y);
        this.setTitle("Job Statistics");
        this.setResizable(false);

        // Create all widgets in the window

        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> {
            this.close();

        });

        List<Pair<String, String>> lineSpecs = new ArrayList<>();

        lineSpecs.add(new Pair<>("Number of completed jobs", "num_completed_jobs"));
        lineSpecs.add(new Pair<>("Number of processed images", "num_processed_images"));
        ICS432ImgApp.filterNames.forEach((f) -> lineSpecs.add(new Pair<>(f + " compute speed (MB/sec)",
                "filter_speed_" + f)));

        VBox layout = new VBox();

        lineSpecs.forEach((s)-> {
            HBox row = new HBox();
            Label prefixLabel = new Label(" " + s.getKey() + ":");
            prefixLabel.setPrefWidth(250);
            prefixLabel.setFont(new Font(14));
            row.getChildren().add(prefixLabel);
            Text valueLabel = new Text(ICS432ImgApp.statistics.toString(s.getValue()));
            valueLabel.setFont(new Font("Arial", 14));

            row.getChildren().add(valueLabel);
            layout.getChildren().add(row);
            ICS432ImgApp.statistics.content.get(s.getValue()).addListener(
                    (observable, oldValue, newValue) -> Platform.runLater(() -> valueLabel.setText(ICS432ImgApp.statistics.toString(s.getValue())))
            );

            HBox row1 = new HBox(5);
            row1.getChildren().add(closeButton);
            layout.getChildren().add(row1);

        });


        // Build the scene
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
}
