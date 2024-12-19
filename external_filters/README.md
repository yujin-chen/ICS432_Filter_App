## External filters

The `external_filters/c_filters` directory contains C implementations for
three image filters. These filters are implemented as part of executable
programs that can be invoked on the command-line, or by the Java code.

### Building filters on Linux 

On a Linux machine, the external filters can be built/installed as follows:

  - `sudo apt update`
  - `sudo apt install gcc cmake libjpeg-dev
  - `cd external_filters/c_filters`
  - `mkdir build`
  - `cd build`
  - `cmake ..`
  - `make`
  - `sudo make install`

This above sequence of commands will result in three executables (`jpegedge`, `jpegfunk1`, and `jpegfunk2`) installed in `/bin` so that they can just be invoked from the command-line.

Programming assignments will ask you to modify the code of these filters (in `external_filters/c_filters/src/`). 

### Building a Docker image with the filters

Because not everybody is on a Linux system, we will create a Docker image with the filters installed in it, and the Java code will invoke the filter programs via Docker!

After installing [Docker](https://docs.docker.com) on your machine, the Docker image can be built easily from the Dockerfile provided for you in `external_filters/Dockerfile`. Specifically:
  
  - `cd external_filter`
  - `docker build --no-cache -t ics432imgapp_c_filters .`

(don't forget the `.`). The above `docker` command will take a while, and create a Linux Docker image called `ics432imgapp_c_filters` in which the three image filter programs implemented in C are compiled and installed (`jpegedge`, `jpegfunk1`, `jpegfunk2`).

To make sure that the Docker image was built correctly:
  - Pick some jpeg image, say in file `input_image.jpg`, in some directory, say `input_image_path`, on your machine

  - Pick some directory on your machine to which an output image should be written, say `output_image_path`

  - Pick some name for the output image file, say `output_image.jpg`

  - Invoke the following command (in the examples below we assume a Linux-like file-system with forward slashes, you have to change that if you're on Windows):

    ```
    docker run --rm -v input_image_path:/tmp/input -v output_image_path:/tmp/output ics432imgapp_c_filters jpegedge /tmp/input/input_image.jpg /tmp/output/output_image.jpg
    ```

  - After this command returns, you should see the output image on your machine in file `output_image_path/output_image.jpg`!!
 

If you're encountering difficulties, ask the instructor. The above demonstrates that we are able to run the image filter programs installed in the Docker container put passing them input files that are stored on your local machine and having them write output files on your local machine as well. In other words, although these programs run on Linux within the container, the container can run on any OS (that's what containers do).

To invoke the external filters, the Java app will simply execute the above (long) `docker` command in an external process.

