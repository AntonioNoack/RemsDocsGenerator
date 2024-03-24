package me.anno.docs.stats

import me.anno.docs.Method
import me.anno.docs.Scope
import me.anno.docs.collectAll
import me.anno.utils.structures.lists.Lists.largestKElements
import me.anno.utils.types.Floats.f3
import kotlin.math.log2

fun main() {
    // done set start and end length to each function
    // collect all functions
    collectAll()
    collectMethods(Scope.all)
    val buckets = IntArray(10)
    for (func in functions) {
        buckets[bucket(func.lines)]++
    }
    // print the longest functions
    //  finally shorten them by splitting them up
    println()
    println("Longest Functions:")
    val largestK = functions.largestKElements(20, Comparator { p0, p1 -> p0.lines.compareTo(p1.lines) })
    for ((idx, func) in largestK.withIndex()) {
        println(
            "${
                (idx + 1).toString().padStart(2)
            }. ${func.scope.combinedName}.${func.method.name}(): ${func.lines} lines"
        )
    }
    println()
    // plot the number of lines per function
    val total = functions.size
    println("Total: $total")
    for ((idx, value) in buckets.withIndex()) {
        val percentile = (0..idx).sumOf { buckets[it] }.toFloat() / total
        println(
            "Bucket[$idx]: ${value.toString().padStart(5)}   // ${percentile.f3()} // ${
                ("${1.shl(idx)}+").padStart(
                    4
                )
            }"
        )
    }
}

class Function(val scope: Scope, val method: Method) {
    val lines = method.endLine - method.startLine + 1
}

val functions = ArrayList<Function>()

fun collectMethods(scope: Scope) {
    if (scope.combinedName == "me.anno.tests") return
    functions.addAll(scope.methods.map { Function(scope, it) })
    for (child in scope.children.values) {
        collectMethods(child)
    }
}

fun bucket(lineCount: Int): Int {
    return log2(lineCount.toDouble()).toInt()
}