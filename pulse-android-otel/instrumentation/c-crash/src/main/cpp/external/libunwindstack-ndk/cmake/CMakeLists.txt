cmake_minimum_required(VERSION 3.4)
project(unwindstack LANGUAGES C CXX ASM)

set(UNWINDSTACK_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../)
set(sources
        ${UNWINDSTACK_ROOT}/ArmExidx.cpp
        ${UNWINDSTACK_ROOT}/DexFiles.cpp
        ${UNWINDSTACK_ROOT}/DwarfCfa.cpp
        ${UNWINDSTACK_ROOT}/DwarfEhFrameWithHdr.cpp
        ${UNWINDSTACK_ROOT}/DwarfMemory.cpp
        ${UNWINDSTACK_ROOT}/DwarfOp.cpp
        ${UNWINDSTACK_ROOT}/DwarfSection.cpp
        ${UNWINDSTACK_ROOT}/Elf.cpp
        ${UNWINDSTACK_ROOT}/ElfInterfaceArm.cpp
        ${UNWINDSTACK_ROOT}/ElfInterface.cpp
        ${UNWINDSTACK_ROOT}/Global.cpp
        ${UNWINDSTACK_ROOT}/JitDebug.cpp
        ${UNWINDSTACK_ROOT}/LocalUnwinder.cpp
        ${UNWINDSTACK_ROOT}/Log.cpp
        ${UNWINDSTACK_ROOT}/MapInfo.cpp
        ${UNWINDSTACK_ROOT}/Maps.cpp
        ${UNWINDSTACK_ROOT}/Memory.cpp
        ${UNWINDSTACK_ROOT}/RegsArm64.cpp
        ${UNWINDSTACK_ROOT}/RegsArm.cpp
        ${UNWINDSTACK_ROOT}/Regs.cpp
        ${UNWINDSTACK_ROOT}/RegsMips64.cpp
        ${UNWINDSTACK_ROOT}/RegsMips.cpp
        ${UNWINDSTACK_ROOT}/RegsX86_64.cpp
        ${UNWINDSTACK_ROOT}/RegsX86.cpp
        ${UNWINDSTACK_ROOT}/Symbols.cpp
        ${UNWINDSTACK_ROOT}/ThreadEntry.cpp
        ${UNWINDSTACK_ROOT}/ThreadUnwinder.cpp
        ${UNWINDSTACK_ROOT}/Unwinder.cpp
        ${UNWINDSTACK_ROOT}/android-base/file.cpp
        ${UNWINDSTACK_ROOT}/android-base/stringprintf.cpp
        ${UNWINDSTACK_ROOT}/android-base/strings.cpp
        ${UNWINDSTACK_ROOT}/android-base/threads.cpp
        ${UNWINDSTACK_ROOT}/procinfo/process.cpp
        # unneeded for bugsnag unwinding use
        # ${UNWINDSTACK_ROOT}/DexFile.cpp
        # ${UNWINDSTACK_ROOT}/MemoryMte.cpp
)
if (${CMAKE_SYSTEM_PROCESSOR} MATCHES i686)
    list(APPEND sources ${UNWINDSTACK_ROOT}/AsmGetRegsX86.S)
elseif (${CMAKE_SYSTEM_PROCESSOR} MATCHES x86_64)
    list(APPEND sources ${UNWINDSTACK_ROOT}/AsmGetRegsX86_64.S)
endif ()

add_library(unwindstack STATIC ${sources})

if (NOT ${CMAKE_SYSTEM_PROCESSOR} MATCHES arm)
    target_compile_definitions(unwindstack PRIVATE EM_ARM=40)
endif ()

set_target_properties(unwindstack PROPERTIES
        CXX_STANDARD 17
)

target_include_directories(unwindstack
        PRIVATE ${UNWINDSTACK_ROOT}
        PUBLIC ${UNWINDSTACK_ROOT}/include
)

add_subdirectory(${UNWINDSTACK_ROOT}/external/lzma lzma)

target_link_libraries(unwindstack log android lzma)
