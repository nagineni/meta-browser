# Recipe files compiles chromium using local chromium source pointed by CHROMIUM_LOCAL_PATH
# 1) Add patches to SRC_URI. Version specific patches should be contained in a
#    "chromium-XX" subdirectory, where XX is the major version. There are also
#    patches that are shared amongst versions but may one day no longer be
#    needed (like unistd2.patch). These do not belong in such a subdirectory,
#    but still need to be explicitely be added. Do NOT add ozone-wayland patches
#    to SRC_URI here!
# 2) Add ozone-wayland patches to the OZONE_WAYLAND_EXTRA_PATCHES variable.
#    The rule with the chromium-XX subdirectory also applies here.
# 3) Set the CHROMIUM_LOCAL_PATH value: "/path/to/my/chromium/checkout/src"
# 4) Optionally, set values for these variables:
#    * CHROMIUM_WAYLAND_DEPENDS
#    * CHROMIUM_WAYLAND_GYP_DEFINES
#    * CHROMIUM_OUT_DIR

LIC_FILES_CHKSUM = "file://LICENSE;md5=537e0b52077bf0a616d0a0c8a79bc9d5"
DESCRIPTION = "Chromium browser"
LICENSE = "BSD"
DEPENDS = "xz-native pciutils pulseaudio cairo nss zlib-native libav cups ninja-native gconf libexif pango libdrm"
RDEPENDS_chromium += "alsa-utils libegl-mesa libglapi libgles1-mesa libgles2-mesa mesa-megadriver libva libgbm libva-wayland"
SRC_URI = "\
        file://include.gypi \
        file://oe-defaults.gypi \
        ${@bb.utils.contains('PACKAGECONFIG', 'component-build', 'file://component-build.gypi', '', d)} \
        file://chromium-43/0004-Remove-hard-coded-values-for-CC-and-CXX.patch \
        file://unistd-2.patch \
        file://google-chrome \
        file://google-chrome.desktop \
        "

# PACKAGECONFIG explanations:
#
# * use-egl : Without this packageconfig, the Chromium build will use GLX for creating an OpenGL context in X11,
#             and regular OpenGL for painting operations. Neither are desirable on embedded platforms. With this
#             packageconfig, EGL and OpenGL ES 2.x are used instead. On by default.
#
# * component-build : Enables component build mode. By default, all of Chromium (with the exception of FFmpeg)
#                     is linked into one big binary. The linker step requires at least 8 GB RAM. Component mode
#                     was created to facilitate development and testing, since with it, there is not one big
#                     binary; instead, each component is linked to a separate shared object.
#                     Use component mode for development, testing, and in case the build machine is not a 64-bit
#                     one, or has less than 8 GB RAM. Off by default.
#

# conditionally add ozone-wayland and its patches to the Chromium sources

# Only enable Wayland.
ENABLE_WAYLAND = "${@base_contains('DISTRO_FEATURES', 'wayland', '1', '0', d)}"

# variable for extra ozone-wayland patches, typically extended by BSP layer .bbappends
# IMPORTANT: do not simply add extra ozone-wayland patches to the SRC_URI in a
# .bbappend, since the base ozone-wayland patches need to be applied first (see below)

OZONE_WAYLAND_EXTRA_PATCHES += " \
        file://chromium-43/0005-Remove-X-libraries-from-GYP-files.patch \
        file://chromium-43/0006-disable-libsecret.patch \
"

# using 00*.patch to skip the WebRTC patches in ozone-wayland
# the WebRTC patches remove X11 libraries from the linker flags, which is
# already done by another patch (see above). Furthermore, to be able to use
# these patches, it is necessary to update the git repository in third_party/webrtc,
# which would further complicate this recipe.
OZONE_WAYLAND_PATCH_FILE_GLOB = "00*.patch"

do_patch[prefuncs] += "${@base_conditional('ENABLE_WAYLAND', '1', 'add_ozone_wayland_patches', '', d)}"

python add_ozone_wayland_patches() {
    import glob
    srcdir = d.getVar('S', True)
    # find all ozone-wayland patches and add them to SRC_URI
    upstream_patches_dir = srcdir + "/ozone/patches"
    upstream_patches = glob.glob(upstream_patches_dir + "/" + d.getVar('OZONE_WAYLAND_PATCH_FILE_GLOB', True))
    upstream_patches.sort()
    for upstream_patch in upstream_patches:
        d.appendVar('SRC_URI', ' file://' + upstream_patch)
    # then, add the extra patches to SRC_URI order matters;
    # extra patches may depend on the base ozone-wayland ones
    d.appendVar('SRC_URI', ' ' + d.getVar('OZONE_WAYLAND_EXTRA_PATCHES'))
}


# include.gypi exists only for armv6 and armv7a and there isn't something like COMPATIBLE_ARCH afaik
COMPATIBLE_MACHINE = "(-)"
COMPATIBLE_MACHINE_i586 = "(.*)"
COMPATIBLE_MACHINE_x86-64 = "(.*)"
COMPATIBLE_MACHINE_armv6 = "(.*)"
COMPATIBLE_MACHINE_armv7a = "(.*)"

inherit gettext

# this makes sure the dependencies for the EGL mode are present; otherwise, the configure scripts
# automatically and silently fall back to GLX
PACKAGECONFIG[use-egl] = ",,virtual/egl virtual/libgles2"

EXTRA_OEGYP =	" \
	-Dlinux_use_bundled_binutils=0 \
	-Dlinux_use_debug_fission=0 \
	-Dangle_use_commit_id=0 \
	-Dclang=0 \
	-Dhost_clang=0 \
	-Dwerror= \
	-Ddisable_fatal_linker_warnings=1 \
	${@base_contains('DISTRO_FEATURES', 'ld-is-gold', '', '-Dlinux_use_gold_binary=0', d)} \
	${@base_contains('DISTRO_FEATURES', 'ld-is-gold', '', '-Dlinux_use_gold_flags=0', d)} \
	-I ${WORKDIR}/oe-defaults.gypi \
	-I ${WORKDIR}/include.gypi \
	${@bb.utils.contains('PACKAGECONFIG', 'component-build', '-I ${WORKDIR}/component-build.gypi', '', d)} \
	-f ninja \
"

CHROMIUM_OUT_DIR ?= "out"

ARMFPABI_armv7a = "${@bb.utils.contains('TUNE_FEATURES', 'callconvention-hard', 'arm_float_abi=hard', 'arm_float_abi=softfp', d)}"

CHROMIUM_EXTRA_ARGS ?= " \
	${@bb.utils.contains('PACKAGECONFIG', 'use-egl', '--use-gl=egl', '', d)} \
"

GYP_DEFINES = "${ARMFPABI} release_extra_cflags='-Wno-error=unused-local-typedefs' sysroot=''"

# Set this variable with the path of your chromium checkout after
# running gclient sync.
CHROMIUM_LOCAL_PATH ?= "/path/to/my/chromium/checkout/src"
S = "${CHROMIUM_LOCAL_PATH}"

# These are present as their own variables, since they have changed between versions
# a few times in the past already; making them variables makes it easier to handle that
CHROMIUM_WAYLAND_DEPENDS = "wayland libxkbcommon"
CHROMIUM_WAYLAND_GYP_DEFINES = "use_ash=1 use_aura=1 chromeos=0 use_ozone=1 use_xkbcommon=1 "

python() {
    if d.getVar('ENABLE_WAYLAND', True) == '1':
        d.appendVar('DEPENDS', ' %s ' % d.getVar('CHROMIUM_WAYLAND_DEPENDS', True))
        d.appendVar('GYP_DEFINES', ' %s ' % d.getVar('CHROMIUM_WAYLAND_GYP_DEFINES', True))
}

do_configure() {
	cd ${S}
	GYP_DEFINES="${GYP_DEFINES}" export GYP_DEFINES
	export GYP_GENERATOR_FLAGS="output_dir=${CHROMIUM_OUT_DIR}"
	# replace LD with CXX, to workaround a possible gyp issue?
	LD="${CXX}" export LD
	CC="${CC}" export CC
	CXX="${CXX}" export CXX
	CC_host="${BUILD_CC}" export CC_host
	CXX_host="${BUILD_CXX}" export CXX_host
	build/gyp_chromium --depth=. ${EXTRA_OEGYP}
}

do_compile() {
	# build with ninja
	ninja -C ${S}/${CHROMIUM_OUT_DIR}/Release ${PARALLEL_MAKE} chrome chrome_sandbox
}

do_install() {
	install -d ${D}${bindir}
	install -m 0755 ${WORKDIR}/google-chrome ${D}${bindir}/

	# Add extra command line arguments to google-chrome script by modifying
	# the dummy "CHROME_EXTRA_ARGS" line
	sed -i "s/^CHROME_EXTRA_ARGS=\"\"/CHROME_EXTRA_ARGS=\"${CHROMIUM_EXTRA_ARGS}\"/" ${D}${bindir}/google-chrome

	install -d ${D}${datadir}/applications
	install -m 0644 ${WORKDIR}/google-chrome.desktop ${D}${datadir}/applications/

	install -d ${D}${bindir}/chrome/
	install -m 0755 ${S}/${CHROMIUM_OUT_DIR}/Release/chrome ${D}${bindir}/chrome/chrome
	install -m 0644 ${S}/${CHROMIUM_OUT_DIR}/Release/resources.pak ${D}${bindir}/chrome/
	install -m 0644 ${S}/${CHROMIUM_OUT_DIR}/Release/icudtl.dat ${D}${bindir}/chrome/
	install -m 0644 ${S}/${CHROMIUM_OUT_DIR}/Release/content_resources.pak ${D}${bindir}/chrome/
	install -m 0644 ${S}/${CHROMIUM_OUT_DIR}/Release/keyboard_resources.pak ${D}${bindir}/chrome/
	install -m 0644 ${S}/${CHROMIUM_OUT_DIR}/Release/chrome_100_percent.pak ${D}${bindir}/chrome/
	install -m 0644 ${S}/${CHROMIUM_OUT_DIR}/Release/product_logo_48.png ${D}${bindir}/chrome/
	install -m 0755 ${S}/${CHROMIUM_OUT_DIR}/Release/libffmpegsumo.so ${D}${bindir}/chrome/
	install -m 0755 ${S}/${CHROMIUM_OUT_DIR}/Release/*.bin ${D}${bindir}/chrome/

	# Always adding this libdir (not just with component builds), because the
	# LD_LIBRARY_PATH line in the google-chromium script refers to it
	install -d ${D}${libdir}/chrome/
	if [ -n "${@bb.utils.contains('PACKAGECONFIG', 'component-build', 'component-build', '', d)}" ]; then
		install -m 0755 ${S}/${CHROMIUM_OUT_DIR}/Release/lib/*.so ${D}${libdir}/chrome/
	fi

	install -d ${D}${sbindir}
	install -m 4755 ${S}/${CHROMIUM_OUT_DIR}/Release/chrome_sandbox ${D}${sbindir}/chrome-devel-sandbox

	install -d ${D}${bindir}/chrome/locales/
	install -m 0644 ${S}/${CHROMIUM_OUT_DIR}/Release/locales/en-US.pak ${D}${bindir}/chrome/locales
}

FILES_${PN} = "${bindir}/chrome/ ${bindir}/google-chrome ${datadir}/applications ${sbindir}/ ${libdir}/chrome/"
FILES_${PN}-dbg += "${bindir}/chrome/.debug/ ${libdir}/chrome/.debug/"

PACKAGE_DEBUG_SPLIT_STYLE = "debug-without-src"
