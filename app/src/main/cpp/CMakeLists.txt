cmake_minimum_required(VERSION 3.22.1)
project(mdigamepad LANGUAGES CXX)

add_library(mdigamepad SHARED native_gamepad.cpp)
find_library(log-lib log)
target_link_libraries(mdigamepad ${log-lib})
