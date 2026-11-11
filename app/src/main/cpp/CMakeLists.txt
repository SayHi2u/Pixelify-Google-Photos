cmake_minimum_required(VERSION 3.22.1)
project(pixelify CXX)

add_library(pixelify SHARED field_setter.cpp)
target_compile_features(pixelify PRIVATE cxx_std_17)
target_link_libraries(pixelify log)
