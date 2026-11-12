import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Clave de firma del release, para distribución por MDM. Vive fuera del repo
// en keystore.properties + release-keystore.jks (los dos gitignored); ver
// docs/RELEASE.md. Sin ese archivo, assembleRelease sigue funcionando pero
// produce un APK sin firmar (no instalable por ningún MDM).
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val hasReleaseSigning = keystoreProperties.containsKey("storeFile")

android {
    namespace = "com.rf.inventario"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rf.inventario"
        minSdk = 24
        targetSdk = 35
        // Cada build que se distribuye por el MDM necesita un versionCode
        // mayor al anterior (así detecta que hay actualización); versionName
        // es solo para mostrar. Ver docs/RELEASE.md.
        versionCode = 2
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        manifestPlaceholders["cleartextAllowed"] = "false"

        // URL del backend Spring (rf-inventario-panel-api). El acceso a MySQL vive
        // solo ahí. 10.0.2.2 = localhost del host visto desde el emulador; para
        // dispositivo físico pasar -PapiBaseUrl=http://<IP-LAN>:8082/
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${project.findProperty("apiBaseUrl") ?: "http://10.0.2.2:8082/"}\"",
        )
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // En desarrollo el backend corre por HTTP en la LAN / emulador.
            manifestPlaceholders["cleartextAllowed"] = "true"
        }
        release {
            // Release exige HTTPS salvo que se compile con -PallowCleartext=true
            // (backend en una LAN cerrada sin certificado). Ver docs/RELEASE.md.
            manifestPlaceholders["cleartextAllowed"] =
                (project.findProperty("allowCleartext") ?: "false").toString()
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "keystore.properties no encontrado: assembleRelease va a producir un APK " +
                        "sin firmar. Ver docs/RELEASE.md para generar la clave de release.",
                )
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)

    // Sync periódica del caché y envío de la cola de operaciones offline.
    implementation(libs.androidx.work.runtime.ktx)

    // Cliente del backend Spring (rf-inventario-panel-api). El acceso a MySQL
    // vive solo en ese backend; la app ya no habla JDBC.
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Caché local de solo lectura: lo último sincronizado, para poder ver el
    // inventario sin conexión. La escritura sigue siendo online-only.
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Camera fallback scanner (works offline, no Google Play Services needed)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
