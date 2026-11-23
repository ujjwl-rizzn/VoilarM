plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.wakewords"
    compileSdk = 34
    defaultConfig { applicationId = "app.voilarm.alarm"; minSdk = 27; targetSdk = 34; versionCode = 2; versionName = "1.1" }
    signingConfigs { create("v") { storeFile = file("voilarm.jks"); storePassword = "voilarm123"; keyAlias = "voilarm"; keyPassword = "voilarm123" } }
    buildTypes { getByName("debug") { signingConfig = signingConfigs.getByName("v") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
