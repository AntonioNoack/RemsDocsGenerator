package me.anno.docs

data class Type(
    val name: CharSequence,
    val generics: List<Type>,
    val superTypes: List<Type>,
    val isNullable: Boolean
) {
    override fun toString(): String {
        val nullable = if (isNullable) "?" else ""
        return name.toString() +
                (if (generics.isEmpty()) "" else "<${generics.joinToString(",")}>") +
                (if (superTypes.isEmpty()) "" else ":${superTypes.joinToString(",")}") + nullable
    }
}
