package io.yannickfan.avero.runtime

import android.os.Build
import io.yannickfan.avero.minecraft.DownloadSpec

enum class RuntimeArch(
    val assetToken: String,
    val androidAbi: String
) {
    ARM("arm", "armeabi-v7a"),
    ARM64("arm64", "arm64-v8a"),
    X86("x86", "x86"),
    X86_64("x86_64", "x86_64");

    companion object {
        fun fromAbi(abi: String): RuntimeArch? = when (abi.lowercase()) {
            "armeabi-v7a", "armeabi" -> ARM
            "arm64-v8a" -> ARM64
            "x86" -> X86
            "x86_64" -> X86_64
            else -> null
        }

        fun current(): RuntimeArch? =
            Build.SUPPORTED_ABIS.firstNotNullOfOrNull(::fromAbi)
    }
}

data class AndroidJavaRuntimePackage(
    val majorVersion: Int,
    val arch: RuntimeArch,
    val download: DownloadSpec,
    val upstreamProject: String = "AngelAuraMC/angelauramc-openjdk-build",
    val upstreamLicense: String = "GPL-2.0 (OpenJDK runtime)"
) {
    val id: String get() = "java$majorVersion-${arch.assetToken}"
    val fileName: String get() = "jre$majorVersion-android-${arch.assetToken}.tar.xz"
}

object AndroidJavaRuntimeCatalog {
    private data class Asset(
        val size: Long,
        val sha256: String
    )

    private val assets: Map<Pair<Int, RuntimeArch>, Asset> = mapOf(
        (8 to RuntimeArch.ARM) to Asset(27_035_664, "9dbee3b09af5f170e2ed9dd596bc81d0e573c88f8bf760c59c8fadbb9073d1e7"),
        (8 to RuntimeArch.ARM64) to Asset(28_026_736, "9a59124d9791957d55c68be664ab76831f336cf2e1e1cd4414220c6fdbf0e06d"),
        (8 to RuntimeArch.X86) to Asset(27_473_340, "b96ce49fab52b28688dccc1a7d85dfc6d0f4048636aac826e1967524b28f75af"),
        (8 to RuntimeArch.X86_64) to Asset(29_132_884, "b1fbcef4965c17925894febe8216d089c6dd47b37950b5f945a89616443c1d0e"),

        (17 to RuntimeArch.ARM) to Asset(25_001_168, "4a9134f1ebf6340dd855805d351712462cddab6f3c2684da7e7da10ccf06648d"),
        (17 to RuntimeArch.ARM64) to Asset(26_900_804, "e162c860fe05ee4a4e4af7606437419879f6c748386a7b09fa77d10db6a64091"),
        (17 to RuntimeArch.X86) to Asset(26_951_040, "223a2d54606a9eb853c8451cf1d6bda1a8f09cb357f40b790140e57d45731ed7"),
        (17 to RuntimeArch.X86_64) to Asset(27_780_012, "893e27d2aed8b40407f29fe939e2a0f193e5d55f72892a303db634b0808a2b61"),

        (21 to RuntimeArch.ARM) to Asset(26_586_292, "96c297487def64666e379a9a363d9955c05b1a0b091b0cf24af88359a66f394a"),
        (21 to RuntimeArch.ARM64) to Asset(28_675_516, "8d41ec401ee59f7722df60ed991f81ad146e130452804bfdd8a05d3436f7bbfe"),
        (21 to RuntimeArch.X86) to Asset(28_689_112, "9b8c7d10c5f751acb3b33506593da44ece52a0fd03e0b3c283ba08a7f285a40f"),
        (21 to RuntimeArch.X86_64) to Asset(29_662_988, "cb88723961f5f9ad63afa1f212eb199816c27cabfd7dc66567bde1d8fb69713b"),

        (25 to RuntimeArch.ARM) to Asset(28_252_088, "9ae13aee9cba7b2d2d8f40965061667e876d7380d866f37a992db3eff296ffb5"),
        (25 to RuntimeArch.ARM64) to Asset(38_031_580, "d3eb7afe2240c26728a1bb440502c5f18ac3883e932d202dd7f0c9bcbbce4c37"),
        (25 to RuntimeArch.X86_64) to Asset(39_061_384, "7fca862ee1b2d5fe23cd9c9c3d9b7ad3c241947ad1a6cc9464ef2e674867105d")
    )

    fun find(
        majorVersion: Int,
        arch: RuntimeArch = requireNotNull(RuntimeArch.current()) {
            "Unsupported Android ABI: ${Build.SUPPORTED_ABIS.joinToString()}"
        }
    ): AndroidJavaRuntimePackage? {
        val asset = assets[majorVersion to arch] ?: return null
        val tag = when (majorVersion) {
            8 -> "download_jre8"
            17 -> "download_jre17"
            21 -> "download_jre21"
            25 -> "download_jre25"
            else -> return null
        }
        val fileName = "jre$majorVersion-android-${arch.assetToken}.tar.xz"
        return AndroidJavaRuntimePackage(
            majorVersion = majorVersion,
            arch = arch,
            download = DownloadSpec(
                url = "https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download/$tag/$fileName",
                sha1 = null,
                size = asset.size,
                path = null,
                sha256 = asset.sha256
            )
        )
    }

    fun supportedMajors(arch: RuntimeArch): List<Int> =
        assets.keys.filter { it.second == arch }.map { it.first }.distinct().sorted()
}
