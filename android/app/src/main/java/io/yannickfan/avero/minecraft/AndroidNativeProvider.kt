package io.yannickfan.avero.minecraft

data class AndroidNativeProviderResolution(
    val providerId: String,
    val libraries: List<LibrarySpec>,
    val nativeArchives: List<NativeArchivePlan> = emptyList()
)

fun interface AndroidNativeProvider {
    fun resolve(
        libraries: List<LibrarySpec>,
        context: RuleContext
    ): AndroidNativeProviderResolution
}
