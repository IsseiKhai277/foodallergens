cmake_minimum_required(VERSION 3.22.1)
project("foodAllergen")

# 1. Build native-lib.cpp into libnative-lib.so
add_library(
        native-lib
        SHARED
        native-lib.cpp
)

# 2. Tell the compiler where to find .h files (Headers)
target_include_directories(
        native-lib
        PRIVATE
        ${CMAKE_SOURCE_DIR}
        ${CMAKE_SOURCE_DIR}/llama
)

# 3. Define the IMPORTED libraries (The .so files)
add_library(ggml-base SHARED IMPORTED)
add_library(ggml-cpu  SHARED IMPORTED)
add_library(ggml       SHARED IMPORTED)
add_library(llama      SHARED IMPORTED)

# 4. ADJUST THESE PATHS: Tell CMake exactly where the .so files are
# ${CMAKE_SOURCE_DIR} is app/src/main/cpp.
# We go up one level to 'main' then into 'jniLibs'
set_target_properties(ggml-base PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml-base.so)

set_target_properties(ggml-cpu PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml-cpu.so)

set_target_properties(ggml PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml.so)

set_target_properties(llama PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libllama.so)

# 5. Link everything together
target_link_libraries(
        native-lib
        ggml-base
        ggml-cpu
        ggml
        llama
        log
        android
        atomic
        c++_shared
)