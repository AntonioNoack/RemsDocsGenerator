package me.anno.docs

import me.anno.docs.ChildIndex.indexChildClasses
import me.anno.docs.Scope.Companion.all
import me.anno.io.files.FileReference
import me.anno.utils.OS.documents
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min

//  - HTML website, maybe MarkDeeper
//  - like Kotlin prototype
//  - a tree on the left side like in Intellij
//  - search bar is the most important element on the website

//  - collect data from all files: classes without their implementation
//  - compress it by combining common terms (?) depends on size
//  - index the most common terms / via compression (?) depends on size

// todo index/store/load readme files
// todo show message for @Deprecated

// todo replace FunctionX<Params,...,RetType> to (Params,...)->RetType...

// todo google-indexability: add links to the tree (?)

// todo Hierarchy object is incomplete src/me/anno/ecs/prefab/Hierarchy.kt
// todo properly find types using reflection, where extension methods are used
// todo make links in documentation link to target
// todo clickable/copyable package path?

// todo when ?search= is used, enter the term in the search input
// todo tests look weird with multiple main()s ->, and please link to the correct files,
// todo include test in build, so we can query its types
// todo TODOs are important details, so include them

// generate space-efficient online documentation, that is also searchable
val modules = listOf(
    "src", "test/src", "KOML/src", "SDF/src", "Bullet/src", "BulletJME/src",
    "Box2D/src", "Recast/src", "Lua/src", "PDF/src",
    "JVM/src", "Mesh/src", "Image/src", "Unpack/src", "Video/src"
)

val src = documents.getChild("IdeaProjects/RemsEngine")
val dst = documents.getChild("IdeaProjects/RemsDocsGenerator/src/docs.zip")

fun main() {
    collectAll()
    indexChildClasses(all)
    writeResult()
}

fun collectAll() {
    for (module in modules) {
        collect(src.getChild(module), module)
    }
}

fun writeResult() {
    // save data into zip file
    val data = all.json().toString().toByteArray()
    ZipOutputStream(dst.outputStream()).use {
        it.putNextEntry(ZipEntry("docs.json"))
        it.write(data)
        it.closeEntry()
    }
}

fun collect(folder: FileReference, module: String) {
    if (folder.isDirectory) {
        for (child in folder.listChildren()) {
            if (child.isDirectory) collect(child, module)
            else when (child.lcExtension) {
                "kt" -> indexKotlin(child, module)
                "md" -> indexMarkdown(child, module)
            }
        }
    } else indexKotlin(folder, module)
}

fun indexKotlin(file: FileReference, module: String) {
    KotlinIndexer(file, module).index()
}

fun indexMarkdown(file: FileReference, module: String) {
    val path0 = file.getParent().absolutePath
    val path = path0
        .substring(min(src.absolutePath.length + module.length + 2, path0.length))
        .split('/')
        .filter { it.isNotEmpty() }
    var scope = all
    for (pth in path) {
        scope = scope.getChild(pth, module)
    }
    scope.keywords.add("#${file.readTextSync()}")
}