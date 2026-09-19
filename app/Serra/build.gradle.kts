// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    id("com.android.application") version "8.13.0" apply false  // Versione definita nel progetto root
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false // Aggiungi questa riga
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
}
