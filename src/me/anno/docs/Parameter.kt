package me.anno.docs

data class Parameter(
    val name: CharSequence, val type: Type,
    val isVararg: Boolean,
    val isCrossInline: Boolean
)
