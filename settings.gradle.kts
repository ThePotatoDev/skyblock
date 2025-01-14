plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "network"

include("plugins")
include("plugins:core")
include("plugins:proxy")
findProject("plugins:proxy")?.name = "proxy"
include("plugins:shared")
findProject("plugins:shared")?.name = "shared"

include("servers:server")
include("servers:spawn")
include("servers:limbo")
include("servers:proxy")
include("servers")
