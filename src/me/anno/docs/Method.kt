package me.anno.docs

class Method(
    val name: CharSequence,
    val generics: List<Type>,
    val params: List<Parameter>,
    val returnType: Type?,
    val keywords: List<CharSequence>
)
