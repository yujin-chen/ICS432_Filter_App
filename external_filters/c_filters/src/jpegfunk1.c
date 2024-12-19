#include <stdio.h>
#include <stdlib.h>
#include <jpeglib.h>
#include <omp.h>
#include <sys/time.h>

#define MIN(x,y) (((x) < (y)) ? (x) : (y))
#define MAX(x,y) (((x) > (y)) ? (x) : (y))

/**
 * @brief Helper data structure to store image dimensions and RGB values
 */
struct rgb_image {
    int height;             // height in pixels
    int width;              // width in pixels
    unsigned char *RGB[3];  // RGB values (height * width of eac)
};

/**
 * @brief Function to free memory for the rgb_image data structure
 * @param img: a pointer to an rgb_image data structure
 */
void free_image(struct rgb_image *img)  {

    for (int rgb=0; rgb < 3; rgb++) {
        free(img->RGB[rgb]);
    }
    free(img);

}

/**
 * @brief Function to read an input (jpeg) image from disk into RAM
 * @param filename: the path to the input image file
 * @return an image data  structure
 */
struct rgb_image *read_input_image(char *filename) {

    FILE *infile;
    if ((infile = fopen(filename, "r")) == NULL) {
        fprintf(stderr, "Could not open file %s for reading", filename);
        exit(1);
    }

    struct rgb_image *image = (struct rgb_image *) calloc(1, sizeof(struct rgb_image));

    struct jpeg_decompress_struct cinfo;
    struct jpeg_error_mgr jerr;
    cinfo.err = jpeg_std_error(&jerr);
    jpeg_create_decompress(&cinfo);

    jpeg_stdio_src(&cinfo, infile);
    jpeg_read_header(&cinfo, TRUE);
    image->width = (int) (cinfo.image_width);
    image->height = (int) (cinfo.image_height);
    jpeg_start_decompress(&cinfo);

    for (int rgb=0; rgb < 3; rgb++) {
        image->RGB[rgb] = (unsigned char *) malloc(sizeof(unsigned char) * image->width * image->height);
    }

    int row_stride = cinfo.output_width * cinfo.output_components;
    JSAMPARRAY buffer;
    buffer = (*cinfo.mem->alloc_sarray)
            ((j_common_ptr) &cinfo, JPOOL_IMAGE, row_stride, 1);

    for (int row = 0; row < cinfo.output_height; row++) {
        (void) jpeg_read_scanlines(&cinfo, buffer, 1);
        unsigned char *pixel_row = (unsigned char *) (buffer[0]);
        for (int col = 0; col < cinfo.output_width; col++) {
            for (int rgb=0; rgb < 3; rgb++) {
                image->RGB[rgb][row * image->width + col] = (unsigned char) (*pixel_row++);
            }
        }
    }

    (void) jpeg_finish_decompress(&cinfo);
    fclose(infile);

    return image;
}

/**
 * @brief Function to create space for the output image in RAM
 * @param input_image: the corresponding input image
 * @return an image data structure
 */
struct rgb_image *create_output_image(struct rgb_image *input_image) {

    struct rgb_image *output_image = (struct rgb_image *) calloc(1, sizeof(struct rgb_image));
    output_image->width = input_image->width;
    output_image->height = input_image->height;
    for (int rgb=0; rgb < 3; rgb++) {
        output_image->RGB[rgb] = (unsigned char *) malloc(
                sizeof(unsigned char) * input_image->width * input_image->height);
    }

    return output_image;
}

/**
 * @brief Function that applies a filter to the input image for generating the output image pixels
 * @param image: an image data structure
 * @param filename: the path to the output image file
 */
void write_output_image(struct rgb_image *image, char *filename) {

    FILE *outfile;
    if ((outfile = fopen(filename, "w")) == NULL) {
        fprintf(stderr, "Could not open file %s for writing", filename);
        exit(1);
    }

    struct jpeg_compress_struct cinfo;
    struct jpeg_error_mgr jerr;
    cinfo.err = jpeg_std_error(&jerr);
    jpeg_create_compress(&cinfo);
    jpeg_stdio_dest(&cinfo, outfile);

    cinfo.image_width = image->width;
    cinfo.image_height = image->height;
    cinfo.input_components = 3;
    cinfo.in_color_space = JCS_RGB;

    jpeg_set_defaults(&cinfo);
    jpeg_set_quality(&cinfo, 100, TRUE);   // Quality = 100
    jpeg_start_compress(&cinfo, TRUE);

    JSAMPROW row_pointer[1];
    int row = 0;
    while (cinfo.next_scanline < cinfo.image_height) {

        unsigned char *row_rgbs = (unsigned char *) calloc(3 * cinfo.image_width, sizeof(unsigned char));
        for (int i = 0; i < cinfo.image_width * 3; i += 3) {
            for (int rgb=0; rgb < 3; rgb++) {
                row_rgbs[i + rgb] = (unsigned char) (image->RGB[rgb][row * image->width + i / 3]);
            }
        }
        row_pointer[0] = row_rgbs;
        (void) jpeg_write_scanlines(&cinfo, row_pointer, 1);

        row++;
    }

    (void) jpeg_finish_compress(&cinfo);
    fclose(outfile);
    jpeg_destroy_compress(&cinfo);
}

/**
 * Helper function to compare bytes
 */
int compare_byte(const void *x, const void  *y) {
    unsigned char *uc_x = (unsigned char *)x;
    unsigned char *uc_y = (unsigned char *)y;
    if (*uc_x < *uc_y) {
        return -1;
    } else if (*uc_x > *uc_y) {
        return 1;
    } else {
        return 0;
    }
}


/**
 * @brief Function to compute the new (i.e., transformed) pixel value
 * @param input_image: input image data structure
 * @param row: pixel row
 * @param col: pixel column
 * @param rgb: RGB channel
 */
unsigned char compute_pixel_value(struct rgb_image *input_image, int row, int col, int channel) {

    double radius =  MAX(1.0,
                         (5.0 * (col) / (double) input_image->width) +
                         (20.0 * ( row) / (double) input_image->height));

    int row_lbound = MAX(0, row - radius);
    int row_ubound = MIN(input_image->height, row + radius);
    int col_lbound = MAX(0, col - radius);
    int col_ubound = MIN(input_image->width, col + radius);

    int num_values = (row_ubound - row_lbound + 1) * (col_ubound - col_lbound + 1);
    unsigned char *values = (unsigned char *)malloc(num_values * sizeof(unsigned char));
    unsigned char *ptr = values;
    for  (int i = row_lbound; i <= row_ubound; i++) {
        for  (int j = col_lbound; j <= col_ubound; j++) {
            *(ptr++) = input_image->RGB[channel][(i) * input_image->width + j];
        }
    }
    qsort(values, num_values, sizeof(unsigned char), compare_byte);
    double funky = MAX(0, (double)values[num_values-1] - (double)values[num_values/2] / 2.0 + (double)values[0]/(4.0));
    return (unsigned char)funky;

}


/**
 * @brief Function that applies a filter to the input image for generating the output image pixels
 * @param input_image: the input image data structure
 * @param output_image: the output image data structure
 */


void apply_filter(struct rgb_image *input_image, struct rgb_image *output_image, int num_threads) {
    int row, col, rgb;


    #pragma omp parallel private(row, col, rgb) num_threads(num_threads)
    {
        int thread_num = omp_get_thread_num();
        struct timeval start, end;

        // Start timer for the current thread
        gettimeofday(&start, NULL);

        #pragma omp for nowait
        for (row = 0; row < input_image->height; row++) {
            for (col = 0; col < input_image->width; col++) {
                for (rgb = 0; rgb < 3; rgb++) {
                    output_image->RGB[rgb][row * input_image->width + col] =
                            compute_pixel_value(input_image, row, col, rgb);
                }
            }
        }

        // Stop timer for the current thread
        gettimeofday(&end, NULL);

        // Calculate and print the thread time
        double thread_time = (end.tv_sec - start.tv_sec) +
                             (end.tv_usec - start.tv_usec) / 1000000.0;
        printf("Thread %d took %.6f seconds\n", thread_num, thread_time);
    }
}

int main(int argc, char **argv) {

    /** Parse Command-Line Arguments **/
    if (argc != 4) {
        fprintf(stderr, "Usage: %s <input jpg file path> <output jpg file path> <Number of DP threads>\n", argv[0]);
        exit(1);
    }
    int num_threads = atoi(argv[3]);
    fprintf(stderr, "Number of threads: %d\n", num_threads);
    if (num_threads <= 0) {
        fprintf(stderr, "Number of threads must be a positive integer.\n");
        exit(1);
    }
    /** Read Input Image into RAM **/
    struct rgb_image *input_image = read_input_image(argv[1]);

    /** Create Output Image in RAM **/
    struct rgb_image *output_image = create_output_image(input_image);

    /** Apply Filter **/
    apply_filter(input_image, output_image, num_threads);

    /** Save Output Image **/
    write_output_image(output_image, argv[2]);

    free_image(input_image);
    free_image(output_image);
}
