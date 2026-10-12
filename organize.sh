#!/bin/bash
# Menyusun file-file datar ke struktur proyek Android sebelum build.
set -e
M=app/src/main
mkdir -p $M/java/id/sawitvision/app $M/res/values $M/res/xml $M/res/drawable $M/assets
mv app-build.gradle.kts app/build.gradle.kts
mv AndroidManifest.xml $M/
mv Analysis.kt AppState.kt Detector.kt ImageUtil.kt MainActivity.kt Storage.kt Ui.kt $M/java/id/sawitvision/app/
mv strings.xml themes.xml $M/res/values/
mv file_paths.xml $M/res/xml/
mv ic_launcher.png $M/res/drawable/
mv sawit_detector.tflite $M/assets/
