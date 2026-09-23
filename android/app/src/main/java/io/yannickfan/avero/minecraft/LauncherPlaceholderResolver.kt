package io.yannickfan.avero.minecraft

object LauncherPlaceholderResolver {
    private val tokenRegex = Regex("""\$\{([^}]+)}""")

    fun resolve(value: String, variables: Map<String, String>): String =
        tokenRegex.replace(value) { match ->
            variables[match.groupValues[1]] ?: match.value
        }

    fun resolveAll(values: List<String>, variables: Map<String, String>): List<String> =
        values.map { resolve(it, variables) }

    fun unresolved(values: List<String>): List<String> =
        values
            .flatMap { value ->
                tokenRegex.findAll(value).map { it.groupValues[1] }.toList()
            }
            .distinct()
            .sorted()
}
