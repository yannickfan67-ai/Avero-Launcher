package io.yannickfan.avero.minecraft

enum class NativeClassifierPolicy {
    DISABLED,
    MOJANG_DESKTOP
}

class NativeLibraryResolver(
    private val rules: RuleEvaluator = RuleEvaluator()
) {
    fun resolve(
        libraries: List<LibrarySpec>,
        context: RuleContext,
        policy: NativeClassifierPolicy = NativeClassifierPolicy.DISABLED
    ): List<NativeArchivePlan> {
        if (policy != NativeClassifierPolicy.MOJANG_DESKTOP) return emptyList()

        return buildList {
            for (library in libraries) {
            if (!rules.isAllowed(library.rules, context)) continue

            val template = library.natives[context.osName] ?: continue
            val classifier = template.replace(
                "$" + "{arch}",
                MinecraftPlatform.classifierArchToken(context.osArch)
            )
            val download = library.classifiers[classifier] ?: continue

                add(
                    NativeArchivePlan(
                        libraryName = library.name,
                        classifier = classifier,
                        download = download,
                        extractExcludes = library.extractExcludes
                    )
                )
            }
        }
    }
}
