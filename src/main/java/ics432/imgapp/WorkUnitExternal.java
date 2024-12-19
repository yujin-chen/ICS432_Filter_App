package ics432.imgapp;

import javafx.scene.control.ProgressBar;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class WorkUnitExternal extends WorkUnit {

    private Path inputFile;
    private Path targetDir;
    private BufferedImage outputImage;
    private Path fileName;
    private String filterName;
    private FileListWithViewPort flvp;
    private String commandFilterName;


    public WorkUnitExternal(Path inputFile, Path targetDir, String filterName, FileListWithViewPort flwvp,
                            ProgressBar progressBar, JobWindow.NumProcessCounter numProcessCounter,
                            int totalImagesInJob, JobWindow.TotalProcessAndWriteTime totalProcessAndWriteTime) {
        super(inputFile, targetDir, filterName, flwvp, progressBar, numProcessCounter, totalImagesInJob, totalProcessAndWriteTime);

        this.inputFile = inputFile;
        this.targetDir = targetDir;
        this.fileName = inputFile.getFileName();
        this.filterName = filterName;
        this.commandFilterName = filterName;
        switch (this.filterName) {
            case "DPEdge":
                this.commandFilterName = "jpegedge";
                break;
            case "DPFunk1":
                this.commandFilterName = "jpegfunk1";
                break;
            case "DPFunk2":
                this.commandFilterName = "jpegfunk2";
                break;
            default:
                throw new RuntimeException("Unknown filter " + filterName);
        }
        this.flvp = flwvp;

    }

    public void processImage(){
        int numThreads = MainWindow.getDPThreadCount();
        String stringNumThread = Integer.toString(numThreads);
        List<String> args = new ArrayList<>();
        args.add("docker");
        args.add("run");
        args.add("--rm");
        args.add("-v");
        args.add(inputFile.getParent()+":/input");
        args.add("-v");
        args.add(targetDir+":/output");
        args.add("ics432imgapp_c_filters");
        args.add(this.commandFilterName);
        args.add("/input/" + inputFile.getFileName());
        args.add("/output/"+this.filterName+"_" + fileName);
        args.add(stringNumThread);
        ProcessBuilder pb = new ProcessBuilder(args);

        try {
            Process p = pb.inheritIO().start(); // The inheritIO() is important!
            int status = p.waitFor();
            if (status != 0) {
                // Ok to just abort if some error
                System.err.println("Processbuilder-created process failed! [FATAL]");
                System.exit(0);
            }
        } catch (InterruptedException ignore) {
        } catch (IOException e) {
            // Ok to just abort if some error
            System.err.println("Processbuilder-created process failed! [FATAL]");
            System.exit(0);
        }
    }
}
