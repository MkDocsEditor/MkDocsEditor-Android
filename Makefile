.PHONY: build

clean:
	./gradlew clean

build:
	./gradlew assembleDebug

buildRelease:
	./gradlew assembleRelease

install: build
	adb install ./app/build/outputs/apk/**/*.apk

installRelease: buildRelease
	adb install ./app/build/outputs/apk/**/*.apk

run: install open

runRelease: installRelease openRelease

open:
	adb shell am start -n "de.markusressel.mkdocseditor.debug/de.markusressel.mkdocseditor.ui.activity.MainActivityCompose" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER

openRelease:
	adb shell am start -n "de.markusressel.mkdocseditor/de.markusressel.mkdocseditor.ui.activity.MainActivityCompose" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
