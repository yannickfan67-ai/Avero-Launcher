package io.yannickfan.avero.minecraft

class NativeLibraryResolver(
    private val rules: RuleEvaluator = RuleEvaluator()
) {
    fun resolve(
        libraries: List<LibrarySpec>,
        context: RuleContext
    ): List<NativeArchivePlan> = buildList {
        for (library in libraries) {
            if (!rules.isAllowed(library.rules, context)) continue

            val template = library.natives[context.osName] ?: continue
            val archPlaceholder = 36.toChar().toString() + "{arch}"
            val classifier = template.replace(
                archPlaceholder,
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
