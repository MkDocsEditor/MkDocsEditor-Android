.PHONY: build test unitTests

clean:
	./gradlew clean

build:
	./gradlew assembleDebug
apk: build
app: build

# All tests. There are only JUnit tests for now, instrumented tests would go here too.
test: unitTests

unitTests:
	./gradlew testDebugUnitTest

buildRelease:
	./gradlew assembleRelease

install: build
	adb install ./app/build/outputs/apk/debug/*.apk

# The release APK is unsigned unless you sign it yourself, see "Build variants" in the README.
installRelease: buildRelease
	adb install ./app/build/outputs/apk/release/*.apk

run: install open

runRelease: installRelease openRelease

open:
	adb shell am start -n "de.markusressel.mkdocseditor.debug/de.markusressel.mkdocseditor.ui.activity.MainActivityCompose" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER

openRelease:
	adb shell am start -n "de.markusressel.mkdocseditor/de.markusressel.mkdocseditor.ui.activity.MainActivityCompose" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
