import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    id("com.vanniktech.maven.publish") version "0.30.0"
}

android {
    namespace = "it.pagopa.io.wallet.cbor"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

mavenPublishing {
    coordinates("it.pagopa.io.wallet.cbor", "cbor", "1.3.0")

    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    pom {
        name.set("IOWallet CBOR Library")
        description.set("A native implementation of CBOR")
        url.set("https://github.com/pagopa/iso18013-android")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://github.com/pagopa/iso18013-android/blob/main/LICENSE")
            }
        }

        developers {
            developer {
                id.set("iowallettech")
                name.set("PagoPA S.p.A.")
                email.set("iowallettech@pagopa.it")
            }
        }

        scm {
            connection.set("scm:git:git://github.com/pagopa/iso18013-android.git")
            developerConnection.set("scm:git:ssh://github.com/pagopa/iso18013-android.git")
            url.set("https://github.com/pagopa/iso18013-android")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.cbor)
    implementation(libs.cose)
    implementation(libs.kotlinx.io.core)
    implementation(libs.kotlinx.io.bytestring)
    implementation(libs.biometric.ktx)
    //Google identity
    implementation(libs.google.identity) {
        exclude(group = "org.bouncycastle")
    }
    // Bouncy castle
    implementation(libs.bouncy.castle.pkix)
    implementation(libs.bouncy.castle.prov)
    implementation(libs.kotlinx.datetime)
    //TESTS
    testImplementation(libs.json)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.bouncy.castle.prov)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}