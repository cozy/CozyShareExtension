# CozyShareExtension
Android Share Extension files - must be integrated AFTER Cordova project generation

Generate the Cozy Drive Android project, the:
- ShareActivity.java -> copy as a sibling of MainActivity.java, ie. mobile/platforms/android/src/io/cozy/drive/mobile folder
- add_to_strings.xml -> copy/paste contents of this file into mobile/platforms/android/res/values/strings.xml
- add_to_AndroidManifest.xml -> copy/paste contents of this file into mobile/platforms/android/AndroidManifest.xml

Then build+debug w/ Gradle.
