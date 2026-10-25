# Cross-compiles a slim libheif.dll for 64-bit Windows: HEIC decoding only (libde265 for HEVC,
# linked in statically, as are the GCC runtimes), so the app ships a single DLL.
FROM debian:trixie
RUN apt-get update && apt-get install -y --no-install-recommends \
        mingw-w64 cmake ninja-build curl ca-certificates pkg-config && rm -rf /var/lib/apt/lists/*
ARG DE265=1.1.3
ARG HEIF=1.23.6
WORKDIR /src
RUN curl -sL https://github.com/strukturag/libde265/archive/refs/tags/v${DE265}.tar.gz | tar xz \
 && curl -sL https://github.com/strukturag/libheif/archive/refs/tags/v${HEIF}.tar.gz | tar xz
COPY toolchain.cmake /src/
RUN cmake -S libde265-${DE265} -B de265 -G Ninja -DCMAKE_TOOLCHAIN_FILE=/src/toolchain.cmake \
        -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=OFF -DENABLE_SDL=OFF -DENABLE_DECODER=OFF -DENABLE_ENCODER=OFF \
        -DCMAKE_INSTALL_PREFIX=/deps \
 && cmake --build de265 && cmake --install de265
RUN cmake -S libheif-${HEIF} -B heif -G Ninja -DCMAKE_TOOLCHAIN_FILE=/src/toolchain.cmake \
        -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON -DCMAKE_PREFIX_PATH=/deps \
        -DCMAKE_C_FLAGS=-DLIBDE265_STATIC_BUILD -DCMAKE_CXX_FLAGS=-DLIBDE265_STATIC_BUILD \
        -DWITH_LIBDE265=ON -DWITH_X265=OFF -DWITH_AOM_DECODER=OFF -DWITH_AOM_ENCODER=OFF -DWITH_DAV1D=OFF \
        -DWITH_RAV1E=OFF -DWITH_SvtEnc=OFF -DWITH_KVAZAAR=OFF -DWITH_OpenH264_DECODER=OFF -DWITH_OpenH264_ENCODER=OFF \
        -DWITH_JPEG_DECODER=OFF -DWITH_JPEG_ENCODER=OFF -DWITH_OpenJPEG_DECODER=OFF -DWITH_OpenJPEG_ENCODER=OFF \
        -DWITH_FFMPEG_DECODER=OFF -DWITH_UVG266=OFF -DWITH_VVDEC=OFF -DWITH_VVENC=OFF -DWITH_LIBSHARPYUV=OFF \
        -DWITH_GDK_PIXBUF=OFF -DWITH_EXAMPLES=OFF -DWITH_EXAMPLE_HEIF_VIEW=OFF -DBUILD_TESTING=OFF \
        -DENABLE_PLUGIN_LOADING=OFF -DWITH_UNCOMPRESSED_CODEC=OFF -DWITH_HEADER_COMPRESSION=OFF \
        -DCMAKE_SHARED_LINKER_FLAGS="-static-libgcc -static-libstdc++ -Wl,-Bstatic,--whole-archive -lwinpthread -Wl,--no-whole-archive" \
        -DCMAKE_INSTALL_PREFIX=/out \
 && cmake --build heif && cmake --install heif
RUN x86_64-w64-mingw32-strip --strip-unneeded /out/bin/libheif.dll
