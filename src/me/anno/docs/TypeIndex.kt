package me.anno.docs

import me.anno.docs.Scope.Companion.invalid

object TypeIndex {

    fun createTypeIndex(scope: Scope): Map<String, Scope> {
        val typeIndex = HashMap<String, Scope>()
        createTypeIndex(scope, typeIndex)
        return typeIndex
    }

    private fun createTypeIndex(scope: Scope, typeIndex: HashMap<String, Scope>) {
        if ("class" in scope.keywords || "interface" in scope.keywords) {
            typeIndex[scope.name] = if (typeIndex[scope.name] == null) scope else invalid
        }
        for (child in scope.children.values) {
            createTypeIndex(child, typeIndex)
        }
    }
}