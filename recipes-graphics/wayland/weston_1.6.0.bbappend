FILESEXTRAPATHS_append := ":${THISDIR}/${PN}"

SRC_URI_append = " \
    file://0001-Add-idle-timeout-option-to-weston-ini.patch \
    file://weston.ini.in \
    file://background.png \
    file://product_logo_24.png \
    "

FILES_${PN} += "${sysconfdir}/xdg"

do_install_append() {
    WESTON_INI_CONFIG=${sysconfdir}/xdg/weston
    install -d ${D}${WESTON_INI_CONFIG}
    install -m 0644 ${WORKDIR}/weston.ini.in ${D}${WESTON_INI_CONFIG}/weston.ini

    install -d ${D}${datadir}/weston
    install ${WORKDIR}/background.png ${D}${datadir}/weston
    install ${WORKDIR}/product_logo_24.png ${D}${datadir}/weston
}

