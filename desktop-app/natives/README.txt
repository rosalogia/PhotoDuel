Native libraries that don't come from Maven, copied as-is into the app's resources folder for each
OS (see prepareNatives in ../build.gradle.kts).

windows-x64/libheif.dll
    libheif 1.23.6 with libde265 1.1.3 linked in, HEIC decoding only, cross-compiled by
    tools/windows-libheif/build.sh. LGPL-3.0 (libheif) and LGPL-3.0 (libde265); as a separate DLL
    it can be replaced by the user. Depends only on KERNEL32 and msvcrt.

Linux builds use the system's libheif (libheif1 on Debian and Ubuntu, libheif on Arch).
