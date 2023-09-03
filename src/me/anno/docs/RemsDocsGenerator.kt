package me.anno.docs

import me.anno.io.files.FileReference
import me.anno.utils.OS.documents
import java.io.EOFException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

//  - HTML website, maybe MarkDeeper
//  - like Kotlin prototype
//  - a tree on the left side like in Intellij
//  - search bar is most important element on site

//  - collect data from all files: classes without their implementation
//  - compress it by combining common terms (?) depends on size
//  - index the most common terms / via compression (?) depends on size

// todo links for all types (most type names are unique)
// todo linkability
// todo google-indexability
// todo highlight currently shown page in tree
// todo when clicking on a search result, also open the tree there
// todo show ordinal for enums

// generate space-efficient online documentation, that is also searchable
val modules = listOf("src", "KOML", "SDF", "Bullet", "Box2D", "Recast", "Lua", "PDF")
fun main() {

    val src = documents.getChild("IdeaProjects/VideoStudio")
    for (module in modules) {
        collect(src.getChild(module), module)
    }

    // save data into zip file
    val dst = documents.getChild("IdeaProjects/RemsDocsGenerator/src/docs.zip")
    val data = all.json().toString().toByteArray()
    ZipOutputStream(dst.outputStream()).use {
        it.putNextEntry(ZipEntry("docs.json"))
        it.write(data)
        it.closeEntry()
    }

}

val all = Scope("", null, modules.first())
val UnitType = Type("Unit", emptyList(), emptyList(), false)

fun collect(folder: FileReference, module: String) {
    if (folder.isDirectory) {
        for (child in folder.listChildren()!!) {
            if (child.isDirectory) collect(child, module)
            else when (child.lcExtension) {
                "kt" -> indexKotlin(child, module)
            }
        }
    } else indexKotlin(folder, module)
}

fun indexKotlin(file: FileReference, module: String) {
    file.readText { text0, e ->

        e?.printStackTrace()

        val text = (text0 ?: "") + "\n"
        var pos = 0
        var line = 1
        val putBack = ArrayList<CharSequence>()

        fun read(): CharSequence? {
            if (putBack.isNotEmpty()) return putBack.removeLast()
            while (pos < text.length) {
                when (text[pos++]) {
                    '/' -> when (text[pos++]) {
                        '/' -> while (pos < text.length && text[pos] != '\n') pos++
                        '*' -> {
                            val pos0 = pos - 2
                            val isJavaDocs = text[pos] == '*'
                            while (pos + 1 < text.length && !(text[pos] == '*' && text[pos + 1] == '/')) {
                                if (text[pos] == '\n') line++
                                pos++
                            }
                            pos += 2 // skip */
                            if (isJavaDocs) {
                                return text.subSequence(pos0, pos)
                            }
                        }

                        else -> {
                            pos-- // undo reading that character
                            text.subSequence(pos - 1, pos)
                        }
                    }

                    '\n' -> line++
                    ' ', '\t', '\r' -> {}
                    in 'A'..'Z', in 'a'..'z', '_' -> {
                        val pos0 = pos - 1
                        while (pos < text.length) {
                            val ch = text[pos++]
                            if (ch !in 'A'..'Z' && ch !in 'a'..'z' && ch !in '0'..'9' && ch !in "_") break
                        }
                        pos-- // last character isn't ours
                        return text.subSequence(pos0, pos)
                    }

                    in ".()[]{}:*<>,=@!+&|%;" -> return text.subSequence(pos - 1, pos)
                    '-' -> return if (text[pos] == '>') {
                        pos++; "->"
                    } else "-"

                    '?' -> return if (text[pos] == ':') {
                        pos++; "?:"
                    } else "?"

                    in '0'..'9' -> {
                        val pos0 = pos - 1
                        while (pos < text.length) {
                            val ch = text[pos++]
                            if (ch !in 'A'..'Z' && ch !in 'a'..'z' && ch !in '0'..'9' && ch !in "_-+") break
                        }
                        pos-- // last character isn't ours
                        return text.subSequence(pos0, pos)
                    }

                    '"' -> {
                        val pos0 = pos - 1
                        if (text[pos] == '"' && text[pos + 1] == '"') {
                            pos = text.indexOf("\"\"\"", pos + 2) + 3
                            if (pos < pos0) throw IllegalStateException("No second triple\" was found in $line, $file")
                            val value = text.subSequence(pos0, pos)
                            line += value.count { it == '\n' }
                            return value
                        } else {
                            while (pos < text.length) {
                                val ch = text[pos++]
                                if (ch == '\"') return text.subSequence(pos0, pos)
                                else if (ch == '\\') pos++
                                else if (ch == '$' && text[pos] == '{') {
                                    pos++ // skip {
                                    var depth = 1
                                    while (depth > 0) {
                                        when (read()) {
                                            "{" -> depth++
                                            "}" -> depth--
                                            null -> throw EOFException()
                                        }
                                    }
                                }
                            }
                        }
                    }

                    '\'' -> {
                        val pos0 = pos - 1
                        if (text[pos] == '\\') pos++
                        pos++ // skip value
                        pos = text.indexOf('\'', pos) + 1
                        if (pos < pos0) return null
                        return text.subSequence(pos0, pos)
                    }

                    '`' -> {
                        val pos0 = pos
                        pos = text.indexOf('`', pos) + 1
                        if (pos < pos0) throw IllegalStateException()
                        return text.subSequence(pos0, pos - 1)
                    }

                    else -> {
                        pos-- // go back one char
                        throw NotImplementedError("Unknown character ${text[pos]}, ${text[pos].code} @$pos in $file")
                    }
                }
            }
            return null
        }

        val keywords = ArrayList<CharSequence>()
        fun readClassBody(scope0: Scope) {
            var scope = scope0
            while (true) {

                fun consume(value: String): Boolean {
                    val v = read()
                    if (v == value) return true
                    if (v != null) putBack.add(v)
                    return false
                }

                fun readPackageName(): CharSequence {
                    var name = read()!!
                    while (consume(".")) {
                        name = "$name.${read()}"
                    }
                    return name
                }

                fun skipParameterValue() {
                    val pos0 = pos
                    val line0 = line
                    while (!consume(",")) {
                        when (val tk = read()) {
                            null -> throw EOFException("$line0, $pos0 in $file")
                            "(", "[", "{", "<" -> {
                                var depth = 1
                                while (depth > 0) {
                                    when (read()) {
                                        null -> throw EOFException()
                                        "(", "[", "{", "<" -> depth++
                                        ")", "]", "}", ">" -> depth--
                                    }
                                }
                            }

                            ")", "]", "}", ">" -> {
                                // out of bounds
                                putBack.add(tk)
                                return
                            }
                        }
                    }
                }

                fun skipEval() {
                    // val l0 = line
                    var li = line
                    var ctr = 0
                    var depth = 0
                    while (true) {
                        when (val tki = read()) {
                            "(", "[", "{" -> depth++
                            ")", "]", "}" -> {
                                li = line
                                depth--
                                // println("skipEval($l0): $line>$li,$depth,$tki")
                                if (depth == -1) {
                                    putBack.add(tki)
                                    return
                                }
                            }

                            "+", "-", "*", "/", "%", "=", "&", "|", ".", "?:" -> {
                                // formula continues
                                if (depth == 0) {
                                    skipEval()
                                    return
                                } // else we don't skip it anyway
                            }

                            null -> return
                            else -> {
                                // println("skipEval($l0): $line>$li,$depth,$tki")
                                if (depth == 0 && line > li && ctr > 0) {
                                    putBack.add(tki)
                                    return
                                }
                            }
                        }
                        if (ctr == 0) li = line
                        ctr++
                    }
                }

                fun skipBlock(open: String, close: String) {
                    var depth = 1
                    while (depth > 0) {
                        when (read()) {
                            open -> depth++
                            close -> depth--
                            null -> return
                        }
                    }
                }

                fun consumeAnnotation() {
                    val name = read()!!
                    if (name == "Docs") {
                        if (consume("(")) {
                            val value = StringBuilder()
                            var depth = 1
                            while (depth > 0) {
                                when (val v = read()) {
                                    "(" -> depth++
                                    ")" -> depth--
                                    null -> return
                                    else -> if (v.startsWith('"')) {
                                        value.append(v.substring(1, v.length - 1))
                                    } else {
                                        value.append(v)
                                    }
                                }
                            }
                            keywords.add("*$value")
                        } else keywords.add("@$name") // weird
                    } else {
                        if (name != "Suppress") keywords.add("@$name")
                        if (consume("(")) skipBlock("(", ")")
                    }
                }

                fun consumeAnnotations() {
                    while (consume("@")) {
                        consumeAnnotation()
                    }
                }

                fun copyKeywords(dst: ArrayList<CharSequence> = ArrayList()): List<CharSequence> {
                    dst.addAll(keywords)
                    keywords.clear()
                    return dst
                }

                val paramKeywords = listOf("private", "projected", "var", "val", "open", "override", "final")
                fun readType(scope: Scope? = null): Type {
                    val name = if (consume("(")) {
                        val bld = StringBuilder()
                        bld.append("(")
                        while (!consume(")")) {
                            bld.append(readType())
                            if (consume(",")) bld.append(',')
                        }
                        bld.append(")")
                        if (consume("->")) {
                            val retType = readType()
                            bld.append("->").append(retType)
                        } // else maybe nullable lambda-type
                        bld.toString()
                    } else {
                        var name = read()!!
                        while (consume(".")) {
                            name = "$name.${read()}"
                        }
                        name
                    }
                    val generics = ArrayList<Type>()
                    if (consume("<")) {
                        while (!consume(">")) {
                            generics.add(readType())
                            consume(",")
                        }
                    }
                    if (scope != null) { // supports arguments
                        val scopeI = scope.getChild(name, module)
                        if (consume("private")) keywords.add("private")
                        if (consume("protected")) keywords.add("protected")
                        if (consume("override")) keywords.add("override")
                        consume("constructor")
                        copyKeywords(scopeI.keywords)
                        if (consume("(")) {
                            while (!consume(")")) {
                                consumeAnnotations()
                                var paramName = read()!!
                                while (paramName.startsWith("/**")) {
                                    keywords.add(paramName)
                                    paramName = read()!!
                                }
                                while (paramName in paramKeywords) {
                                    keywords.add(paramName)
                                    paramName = read()!!
                                }
                                if (!consume(":")) throw IllegalStateException("Expected colon for type, but got ${read()} in $line, $file")
                                val type = readType(null)
                                if ("var" in keywords || "val" in keywords) {
                                    scopeI.fields.add(Field(paramName, type, copyKeywords()))
                                }
                                if (consume("=")) skipParameterValue()
                                consume(",")
                            }
                        }
                    }
                    val superTypes = ArrayList<Type>()
                    if (consume(":")) {
                        do {
                            superTypes.add(readType())
                            if (consume("(")) {
                                // skip arguments
                                var depth = 1
                                while (depth > 0) {
                                    when (read()) {
                                        "(" -> depth++
                                        ")" -> depth--
                                        null -> throw EOFException()
                                    }
                                }
                            }
                        } while (consume(","))
                    }
                    val isNullable = consume("?")
                    return Type(name, generics, superTypes, isNullable)
                }

                when (val tk = read() ?: break) {
                    "package" -> {
                        do {
                            scope = scope.getChild(read()!!, module)
                        } while (consume("."))
                    }

                    "import" -> {
                        val name = readPackageName()
                        scope.imports.add(name)
                    }

                    "class", "interface" -> {
                        keywords.add(tk)
                        val type = readType(scope)
                        val child = scope.getChild(type.name, module)
                        child.superTypes.addAll(type.superTypes)
                        child.generics.addAll(type.generics)
                        if (consume("{")) {
                            copyKeywords(child.keywords)
                            readClassBody(child)
                        }
                    }

                    "fun" -> {
                        // read generics
                        val generics = ArrayList<Type>()
                        if (consume("<")) {
                            while (!consume(">")) {
                                generics.add(readType())
                                consume(",")
                            }
                        }
                        var name = read()!!
                        if (name == "interface") {
                            // read fun interface
                            keywords.add("fun")
                            keywords.add("interface")
                            val type = readType(scope)
                            if (consume("{")) {
                                val child = scope.getChild(type.name, module)
                                copyKeywords(child.keywords)
                                readClassBody(child)
                            }
                        } else {
                            // read function
                            while (true) {
                                if (consume("?")) {
                                    name = "$name?"
                                } else if (consume("<")) {
                                    name = "$name<"
                                    while (!consume(">")) {
                                        name = "$name${readType()}"
                                        if (consume(",")) name = "$name,"
                                    }
                                    name = "$name>"
                                } else if (consume(".")) {
                                    name = "$name.${read()!!}"
                                } else break
                            }
                            // read actual function
                            val params = ArrayList<Parameter>()
                            if (!consume("(")) throw IllegalStateException("Missing opening brackets for fun in $line, $file")
                            while (!consume(")")) {
                                consumeAnnotations()
                                val isVararg = consume("vararg")
                                val isCrossInline = consume("crossinline")
                                val paramName = read()!!
                                if (!consume(":")) throw IllegalStateException("Missing type in $line, $file")
                                val type = readType()
                                params.add(Parameter(paramName, type, isVararg, isCrossInline))
                                if (consume("=")) {
                                    skipParameterValue()
                                }
                                consume(",")
                            }
                            var returnType: Type? = if (consume(":")) {
                                readType()
                            } else UnitType
                            if (consume("=")) {
                                returnType = scope.findMethodReturnType(name, params)
                                skipEval()
                            } else if (consume("{")) {
                                skipBlock("{", "}")
                            }
                            scope.methods.add(Method(name, generics, params, returnType, copyKeywords()))
                        }
                    }

                    "constructor" -> {
                        // read generics
                        val generics = ArrayList<Type>()
                        if (consume("<")) {
                            while (!consume(">")) {
                                generics.add(readType())
                                consume(",")
                            }
                        }
                        val name = ""
                        val params = ArrayList<Parameter>()
                        if (!consume("(")) throw IllegalStateException("Missing opening brackets for fun")
                        while (!consume(")")) {
                            consumeAnnotations()
                            val isVararg = consume("vararg")
                            val isCrossInline = consume("crossinline")
                            val paramName = read()!!
                            if (!consume(":")) throw IllegalStateException("Missing type in $line, $file")
                            val type = readType()
                            params.add(Parameter(paramName, type, isVararg, isCrossInline))
                            if (consume("=")) {
                                skipParameterValue()
                            }
                            consume(",")
                        }
                        if (consume(":")) {
                            if (consume("this") || consume("super")) {
                                if (!consume("(")) throw IllegalStateException("Expected ( after :this/:super")
                                skipBlock("(", ")")
                            } else throw IllegalStateException("Expected this or super")
                        }
                        if (consume("{")) {
                            skipBlock("{", "}")
                        }
                        scope.methods.add(Method(name, generics, params, null, copyKeywords()))
                    }

                    "val", "var" -> {
                        keywords.add(tk)
                        var name = read()!!
                        while (consume(".")) {
                            name = "$name.${read()}"
                        }
                        var type = if (consume(":")) readType() else scope.findFieldType(name)
                        if (consume("=") || consume("by")) {
                            skipEval()
                        }
                        if (consume("private")) keywords.add("private-get")
                        if (consume("get")) {
                            if (read() != "(") throw IllegalStateException()
                            if (read() != ")") throw IllegalStateException()
                            if (consume(":")) {
                                type = readType()
                            }
                            if (consume("{")) {
                                skipBlock("{", "}")
                            } else if (consume("=")) {
                                skipEval()
                            } else throw IllegalStateException("Expected { or = after get in $line, $file")
                        }
                        consume("private-set")
                        if (consume("set")) {
                            if (consume("(")) {
                                if (read() != null && read() == ")") {
                                    if (consume("{")) {
                                        skipBlock("{", "}")
                                    } else if (consume("=")) {
                                        skipEval()
                                    } else throw IllegalStateException("Expected {/= after set in $line, $file")
                                } else throw IllegalStateException("Expected ?){ after set in $line, $file")
                            }
                        }
                        scope.fields.add(Field(name, type, copyKeywords()))
                    }

                    "open", "protected", "private", "public", "abstract", "override", "operator",
                    "final", "inline", "reified", "const", "lateinit", "data", "annotation" -> {
                        keywords.add(tk)
                    }

                    "@" -> consumeAnnotation()

                    "init" -> {
                        if (consume("{")) {
                            skipBlock("{", "}")
                        } else throw IllegalStateException("Invalid init{} block?")
                    }

                    "companion" -> {
                        if (read() != "object") throw IllegalStateException("Expected companion object")
                        val hasName = !consume("{")
                        val name = if (hasName) read()!! else "Companion"
                        if (hasName && !consume("{")) throw IllegalStateException("Expected {")
                        val child = scope.getChild(name, module)
                        keywords.add("companion")
                        copyKeywords(child.keywords)
                        readClassBody(child)
                    }

                    "object" -> {
                        val name = read()!!
                        val child = scope.getChild(name, module)
                        keywords.add("object")
                        copyKeywords(child.keywords)
                        if (consume(":")) {
                            child.superTypes.add(readType())
                            if (consume("(")) {
                                skipBlock("(", ")")
                            }
                            while (consume(",")) {
                                child.superTypes.add(readType())
                            }
                        }
                        if (consume("{")) {
                            readClassBody(child)
                        }
                    }

                    "}" -> return // return from this function of reading the class body
                    "else" -> {
                        // forgotten remains of previous thing
                        skipEval()
                    }

                    "typealias" -> {
                        readType()
                        if (read() != "=") throw IllegalStateException()
                        readType()
                    }

                    "enum" -> {
                        if (!consume("class")) throw IllegalStateException("Expected class after enum")
                        keywords.add("enum")
                        val type = readType(scope)
                        val child = scope.getChild(type.name, module)
                        child.generics.addAll(type.generics)
                        child.superTypes.addAll(type.superTypes)
                        copyKeywords(child.keywords)
                        if (consume("{")) {
                            var i = 0
                            while (!consume(";")) {
                                val name = read()!!
                                if (name == "}") {
                                    putBack.add("}")
                                    break
                                }
                                if (name.startsWith("/**")) {
                                    keywords.add(name)
                                } else if (name[0] in 'A'..'Z' || name[0] in 'a'..'z') {
                                    if (consume("(")) {
                                        // parameters
                                        skipBlock("(", ")")
                                    }
                                    val child1 = child.getChild(name, module)
                                    child1.enumOrdinal = i++
                                    copyKeywords(child1.keywords)
                                    if (consume("{")) {
                                        // custom body
                                        readClassBody(child1)
                                    }
                                    consume(",")
                                } else throw NotImplementedError("Unknown token? $name in $file:$line,$pos")
                            }
                            readClassBody(child)
                        }
                    }

                    else -> {
                        if (tk.startsWith("/**")) {
                            keywords.add(tk)
                        } else println("Ignored $tk in $file:$line, $pos!")
                    }
                }
            }
        }
        readClassBody(all)

    }
}