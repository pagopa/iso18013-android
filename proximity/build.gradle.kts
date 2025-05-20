import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    id("kotlin-parcelize")
    id("com.vanniktech.maven.publish") version "0.30.0"

}

tasks.matching { it.name.contains("javaDocReleaseGeneration", ignoreCase = true) }
    .configureEach { enabled = false }

android {
    namespace = "it.pagopa.io.wallet.proximity"
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
    coordinates("it.pagopa.io.wallet.proximity", "proximity", "1.3.0")

    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    pom {
        name.set("IOWallet Proximity Library")
        description.set("A native implementation of iso18013-5")
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
    implementation(project(":cbor"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.io.core)
    // to parse device request
    implementation(libs.cbor)
    implementation(libs.google.identity) {
        exclude(group = "org.bouncycastle")
    }
    implementation(libs.google.identity.android) {
        exclude(group = "org.bouncycastle")
    }
    implementation(libs.google.identity.mdoc) {
        exclude(group = "org.bouncycastle")
    }
    implementation(libs.bouncy.castle.prov)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}