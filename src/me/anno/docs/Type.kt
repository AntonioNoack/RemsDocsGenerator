package me.anno.docs

data class Type(
    val name: CharSequence,
    val generics: List<Type>,
    val superTypes: List<Type>,
    val isNullable: Boolean
) {

    private fun formatGenerics() = if (generics.isEmpty()) "" else "<${generics.joinToString(",")}>"
    private fun formatSuperTypes() = if (superTypes.isEmpty()) "" else ":${superTypes.joinToString(",")}"
    private fun formatNullable() = if (isNullable) "?" else ""
    private fun formatName() = name.toString()
    override fun toString(): String {
        return formatName() + formatGenerics() + formatSuperTypes() + formatNullable()
    }

    companion object {
        val UnknownType = Type("?", emptyList(), emptyList(), false)
        val StarType = Type("*", emptyList(), emptyList(), false)
        val UnitType = Type("Unit", emptyList(), emptyList(), false)
    }
}
