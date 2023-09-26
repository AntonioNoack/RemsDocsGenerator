package me.anno.docs

import me.anno.docs.Scope.Companion.all
import me.anno.docs.Scope.Companion.invalid

object ChildIndex {

    fun indexChildClasses(scope: Scope) {
        val typeIndex = TypeIndex.createTypeIndex(all)
        indexChildClasses(scope, typeIndex)
    }

    private fun indexChildClasses(scope: Scope, typeIndex: Map<String, Scope>) {
        for (type in scope.superTypes) {
            val sc = typeIndex[type.name]
            if (sc != null && sc != invalid) {
                sc.childClasses.add(scope)
            }
        }
        for (child in scope.children.values) {
            indexChildClasses(child, typeIndex)
        }
    }
}