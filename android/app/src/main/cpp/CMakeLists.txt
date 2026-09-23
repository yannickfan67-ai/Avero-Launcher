cmake_minimum_required(VERSION 3.22.1)

project(avero_jvm LANGUAGES CXX)

add_library(
    avero_jvm
    SHARED
    avero_jvm_bridge.cpp
)

target_compile_features(avero_jvm PRIVATE cxx_std_20)
target_compile_options(avero_jvm PRIVATE -Wall -Wextra -Werror=return-type)

find_library(log-lib log)
find_library(dl-lib dl)
find_library(android-lib android)

target_link_libraries(
    avero_jvm
    ${log-lib}
    ${dl-lib}
    ${android-lib}
)
