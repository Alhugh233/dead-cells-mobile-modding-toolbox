cmake_minimum_required(VERSION 3.31.6)

project("asset_replacer")

add_library(asset_replacer SHARED
    src/main/cpp/native_init.cpp
    src/main/cpp/pak_tool.cpp
    src/main/cpp/atlas_pack.cpp
    src/main/cpp/log_bridge.cpp
)

target_compile_options(asset_replacer PRIVATE
    -std=c++20
    -Wall
    -Wextra
    -Wno-unused-parameter
    -fvisibility=hidden
    -fPIC
)

target_link_libraries(asset_replacer
    android
    dl
    log
)
