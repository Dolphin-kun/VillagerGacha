plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.1"

// See https://stonecutter.kikugie.dev/wiki/v2/guides/start/comment-overview
stonecutter {
    parameters {
        replacements {
            // Mojang renamed ResourceLocation -> Identifier in 26.x.
            // Source is written with 26.1 names (Identifier); swap for older MC.
            string(current.parsed < "26.1") {
                replace("Identifier", "ResourceLocation")
                replace(".identifier()", ".location()")
            }
        }
    }
}
