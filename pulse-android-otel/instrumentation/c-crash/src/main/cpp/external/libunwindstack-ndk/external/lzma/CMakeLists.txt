cmake_minimum_required(VERSION 3.4)
project(lzma)

add_library(lzma STATIC
    7zAlloc.c
    7zArcIn.c
    7zBuf2.c
    7zBuf.c
    7zCrc.c
    7zCrcOpt.c
    7zDec.c
    7zFile.c
    7zStream.c
    Aes.c
    AesOpt.c
    Alloc.c
    Bcj2.c
    Bra86.c
    Bra.c
    BraIA64.c
    CpuArch.c
    Delta.c
    LzFind.c
    Lzma2Dec.c
    Lzma2Enc.c
    Lzma86Dec.c
    Lzma86Enc.c
    LzmaDec.c
    LzmaEnc.c
    LzmaLib.c
    Ppmd7.c
    Ppmd7Dec.c
    Ppmd7Enc.c
    Sha256.c
    Sort.c
    Xz.c
    XzCrc64.c
    XzCrc64Opt.c
    XzDec.c
    XzEnc.c
    XzIn.c
)

target_compile_options(lzma PRIVATE
    -D_7ZIP_ST
    -Wno-empty-body
    -Wno-enum-conversion
    -Wno-logical-op-parentheses
    -Wno-self-assign
)

target_include_directories(lzma PUBLIC .)
