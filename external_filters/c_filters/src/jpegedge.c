#include <stdio.h>
#include <stdlib.h>
#include <jpeglib.h>
#include <setjmp.h>
#include <math.h>
#include <omp.h>

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
 * @brief Function to compute the new (i.e., transformed) pixel value
 * @param input_image: input image data structure
 * @param row: pixel row
 * @param col: pixel column
 * @param rgb: RGB channel
 */
unsigned char compute_pixel_value(struct rgb_image *input_image, int row, int col, int channel) {

    // If a border pixel, return 0
    if (row == 0 || col == 0 || row == input_image->height-1 || col == input_image->width -1) {
        return 0;
    }

    double Gx[3][3] = {{-1, 0, 1}, {-2, 0, 2}, {-1, 0, 1}};
    double Gy[3][3] = {{-1, -2, -1}, {0, 0, 0}, {1, 2, 1}};
    double S1 = 0;
    double S2 = 0;

    for (int i = -1; i <= +1; i++) {
        for (int j = -1; j <= +1; j++) {
            S1 += Gx[i+1][j+1] * input_image->RGB[channel][(row + i) * input_image->width + col + j];
            S2 += Gy[i+1][j+1] * input_image->RGB[channel][(row + i) * input_image->width + col + j];
        }
    }

    double mag = sqrt(S1*S1 + S2*S2);
    double value = MAX(mag,70.0);
    return (unsigned char) value;

}


/**
 * @brief Function that applies a filter to the input image for generating the output image pixels
 * @param input_image: the input image data structure
 * @param output_image: the output image data structure
 */
void apply_filter(struct rgb_image *input_image, struct rgb_image *output_image, int num_threads){
    int row, col, rgb;

    #pragma omp parallel for private(row, col, rgb) num_threads(num_threads)
    for (row = 0; row < input_image->height; row++) {
        for (col = 0; col < input_image->width; col++) {
            for (rgb = 0; rgb < 3; rgb++) {
                output_image->RGB[rgb][row * input_image->width + col] =
                    compute_pixel_value(input_image, row, col, rgb);
            }
        }
    }
}

int main(int argc, char **argv) {

    /** Parse Command-Line Arguments **/
    if (argc != 4) {
        fprintf(stderr, "Usage: %s <input jpg file path> <output jpg file path> <number of data parellel thread>\n", argv[0]);
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
