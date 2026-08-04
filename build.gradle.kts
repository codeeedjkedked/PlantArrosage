// Volontairement dépourvu de bloc `plugins {}`.
//
// Déclarer ici `id("com.android.application") ... apply false` forcerait Gradle à résoudre le
// marker de l'Android Gradle Plugin dès la configuration du projet racine — y compris quand seul
// :core est demandé, et y compris avec `apply false`. Chaque module déclare donc ses propres
// plugins via les alias du version catalog (gradle/libs.versions.toml).

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
