cmake_minimum_required(VERSION 3.2)
project(ics432imgapp_c_filters C)

add_compile_options(-fopenmp)
add_link_options(-fopenmp)

# source files
set(JPEGEDGE_SOURCE_FILES src/jpegedge.c)
set(JPEGFUNK1_SOURCE_FILES src/jpegfunk1.c)
set(JPEGFUNK2_SOURCE_FILES src/jpegfunk2.c)

find_library(JPEG_LIB NAMES libjpeg jpeg)

include_directories(/usr/include /usr/local/include /opt/local/include)

add_executable(jpegedge ${JPEGEDGE_SOURCE_FILES})
add_executable(jpegfunk1 ${JPEGFUNK1_SOURCE_FILES})
add_executable(jpegfunk2 ${JPEGFUNK2_SOURCE_FILES})

target_link_libraries(jpegedge ${JPEG_LIB} m)
target_link_libraries(jpegfunk1 ${JPEG_LIB} m)
target_link_libraries(jpegfunk2 ${JPEG_LIB} m)

install(TARGETS jpegedge DESTINATION bin)
install(TARGETS jpegfunk1 DESTINATION bin)
install(TARGETS jpegfunk2 DESTINATION bin)
