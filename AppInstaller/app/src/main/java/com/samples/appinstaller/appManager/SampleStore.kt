package com.samples.appinstaller.appManager

import com.samples.appinstaller.R

val SampleStoreDB = mapOf(
    "com.acme.spaceshooter" to AppPackage(
        id = "com.acme.spaceshooter",
        name = "Space Shooter",
        company = "ACME Inc.",
        icon = R.mipmap.ic_app_spaceshooter_round
    ),
    "com.champollion.pockettranslator" to AppPackage(
        id = "com.champollion.pockettranslator",
        name = "Pocket Translator",
        company = "Champollion SA",
        icon = R.mipmap.ic_app_pockettranslator_round
    ),
    "com.echolabs.citymaker" to AppPackage(
        id = "com.echolabs.citymaker",
        name = "City Maker",
        company = "Echo Labs Ltd",
        icon = R.mipmap.ic_app_citymaker_round
    ),
    "com.paca.nicekart" to AppPackage(
        id = "com.paca.nicekart",
        name = "Nice Kart",
        company = "PACA SARL",
        icon = R.mipmap.ic_app_nicekart_round
    ),
)