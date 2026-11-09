rootProject.name = "netbiodyn"

include("core")
include("web")

include("compose:shared")

// Le module `db` (serveur REST Ktor) est optionnel.
// Il nécessite un JDK complet (avec javac), pas seulement un JRE.
// Pour l'inclure, décommentez la ligne ci-dessous et installez un JDK :
//   sudo apt-get install -y openjdk-21-jdk-headless
// include("db")

dependencyResolutionManagement {
    versionCatalogs { create("libs") { from(files("versions.toml")) } }
}