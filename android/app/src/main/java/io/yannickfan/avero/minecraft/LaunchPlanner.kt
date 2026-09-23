package io.yannickfan.avero.minecraft

class LaunchPlanner {
    fun createVanillaPlan(
        metadata: MinecraftVersionMetadata,
        instance: LauncherInstance
    ): LaunchPlan {
        require(instance.loader == Loader.VANILLA) {
            "Loader-specific metadata must be normalized before launch planning"
        }

        val classpath = buildList {
            metadata.libraries.mapNotNullTo(this) { it.artifact?.path }
            add("versions/${metadata.id}/${metadata.id}.jar")
        }

        val jvm = buildList {
            add("-Xms512M")
            add("-Xmx${instance.memoryMb}M")
            addAll(metadata.jvmArguments)
        }

        return LaunchPlan(
            versionId = metadata.id,
            mainClass = metadata.mainClass,
            javaMajorVersion = instance.javaMajorVersion ?: metadata.javaMajorVersion,
            classpathEntries = classpath,
            jvmArguments = jvm,
            gameArguments = metadata.gameArguments
        )
    }
}
