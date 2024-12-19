package ics432.imgapp;

import javafx.beans.property.*;
import java.util.HashMap;


/**
 * A class that defines the statistics object that keeps track of the statistics of the application
 */
class Statistics {
    public final HashMap<String, SimpleDoubleProperty> content;

    /**
     * Constructor
     */
     public Statistics(){
        this.content = new HashMap<>();
        this.content.put("num_completed_jobs", new SimpleDoubleProperty(0));
        this.content.put("num_processed_images", new SimpleDoubleProperty(0));
        ICS432ImgApp.filterNames.forEach((t) -> {
            this.content.put("filter_bytes_" + t,
                    new SimpleDoubleProperty(0));
            this.content.put("filter_time_" + t,
                    new SimpleDoubleProperty(0));
            this.content.put("filter_speed_" + t,
                    new SimpleDoubleProperty(0));
        });
     }

    /**
     * Method to update the number of job completed
     */
     public synchronized void newlyCompletedJob() {
         SimpleDoubleProperty p1 = this.content.get("num_completed_jobs");
         p1.set(p1.get() + 1);
     }

    /**
     *
     * Method to update the number of images processed for thread
     */
    public synchronized void newlyCompletedImageThread() {
        SimpleDoubleProperty p1 = this.content.get("num_processed_images");
        p1.set(p1.get() + 1);
    }
    /**
     * Method to update the statistics of the filter after processing the Job. Specified for threads
     */
    public synchronized void newlyProcessedJobThread(String filterName, double mb, double sec) {
        SimpleDoubleProperty p1 = this.content.get("filter_bytes_" + filterName);
        p1.set(p1.get() + mb);
        SimpleDoubleProperty p2 = this.content.get("filter_time_" + filterName);
        p2.set(p2.get() + sec);
        SimpleDoubleProperty p3 = this.content.get("filter_speed_" + filterName);
        p3.set(p1.get() / p2.get());
    }

    /**
     * Method to update the number of images processed and the statistics of the filter after processing the image
     * @param filterName The name of the filter
     * @param mb The size of the image in MB
     * @param sec The time taken to process the image
     */
     public synchronized void newlyProcessedImage(String filterName, double mb, double sec) {
         SimpleDoubleProperty p1 = this.content.get("num_processed_images");
         p1.set(p1.get() + 1);
         SimpleDoubleProperty p2 = this.content.get("filter_bytes_" + filterName);
         p2.set(p2.get() + mb);
         SimpleDoubleProperty p3 = this.content.get("filter_time_" + filterName);
         p3.set(p3.get() + sec);
         SimpleDoubleProperty p4 = this.content.get("filter_speed_" + filterName);
         p4.set(p2.get() / p3.get());
     }

    /**
     *
     * @param pName The name of the property
     * @return the updated value of the property to string and display
     */
    public String toString(String pName) {
        SimpleDoubleProperty p = this.content.get(pName);
        if (pName.equals("num_completed_jobs") || pName.equals("num_processed_images")) {
            return Integer.toString((int)p.get());
        } else {
            return String.format("%.2f", p.get());
        }
    }

}