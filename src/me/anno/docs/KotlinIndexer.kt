package me.anno.docs

import me.anno.docs.Scope.Companion.all
import me.anno.docs.Type.Companion.UnitType
import me.anno.io.files.FileReference
import java.io.EOFException

class KotlinIndexer(private val file: FileReference, private val module: String) {

    companion object {
        private val paramKeywords = listOf("private", "projected", "var", "val", "open", "override", "final", "vararg")
    }

    private var pos = 0
    private var lineNumber = 1
    private val putBack = ArrayList<CharSequence>()
    private val keywords = ArrayList<CharSequence>()

    private lateinit var text: String

    private fun read(): CharSequence? {
        if (putBack.isNotEmpty()) return putBack.removeLast()
        while (pos < text.length) {
            when (text[pos++]) {
                '/' -> when (text[pos++]) {
                    '/' -> while (pos < text.length && text[pos] != '\n') pos++
                    '*' -> {
                        val pos0 = pos - 2
                        val isJavaDocs = text[pos] == '*'
                        while (pos + 1 < text.length && !(text[pos] == '*' && text[pos + 1] == '/')) {
                            if (text[pos] == '\n') lineNumber++
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

                '\n' -> lineNumber++
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
                        if (pos < pos0) throw IllegalStateException("No second triple\" was found in $lineNumber, $file")
                        val value = text.subSequence(pos0, pos)
                        lineNumber += value.count { it == '\n' }
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

    private fun consume(value: String): Boolean {
        val v = read()
        if (v == value) return true
        if (v != null) putBack.add(v)
        return false
    }

    private fun skipParameterValue() {
        val pos0 = pos
        val line0 = lineNumber
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

    private fun skipEval() {
        // val l0 = line
        var li = lineNumber
        var ctr = 0
        var depth = 0
        while (true) {
            when (val tki = read()) {
                "(", "[", "{" -> depth++
                ")", "]", "}" -> {
                    li = lineNumber
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
                    if (depth == 0 && lineNumber > li && ctr > 0) {
                        putBack.add(tki)
                        return
                    }
                }
            }
            if (ctr == 0) li = lineNumber
            ctr++
        }
    }

    private fun skipBlock(open: String, close: String) {
        var depth = 1
        while (depth > 0) {
            when (read()) {
                open -> depth++
                close -> depth--
                null -> return
            }
        }
    }

    private fun consumeAnnotation() {
        val name = read()!!
        if (name == "Docs") {
            if (consume("(")) readDocs()
            else keywords.add("@$name") // weird
        } else {
            if (name != "Suppress" && name != "JvmStatic" && name != "JvmField") keywords.add("@$name")
            if (consume("(")) skipBlock("(", ")")
        }
    }

    private fun readDocs() {
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
    }

    private fun consumeAnnotations() {
        while (consume("@")) {
            consumeAnnotation()
        }
    }

    private fun copyKeywords(dst: ArrayList<CharSequence> = ArrayList()): List<CharSequence> {
        dst.addAll(keywords)
        keywords.clear()
        return dst
    }

    private fun readType(scope: Scope? = null): Type {
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
        val generics = readGenerics()
        if (scope != null) { // supports arguments
            val startLine = lineNumber
            val scopeI = getChildScope(scope, name)
            copyKeywords(scopeI.keywords)
            if (consume("private")) keywords.add("private")
            if (consume("protected")) keywords.add("protected")
            if (consume("override")) keywords.add("override")
            consume("constructor")
            if (consume("(")) {
                val kw = copyKeywords()
                val params = ArrayList<Parameter>()
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
                    if (!consume(":")) throw IllegalStateException("Expected colon for type, but got ${read()} in $lineNumber, $file")
                    val type = readType()
                    params.add(Parameter(paramName, type, "vararg" in keywords, false))
                    if ("var" in keywords || "val" in keywords) {
                        scopeI.fields.add(Field(paramName, type, copyKeywords()))
                    }
                    if (consume("=")) skipParameterValue()
                    consume(",")
                }
                val endLine = lineNumber
                scopeI.methods.add(Method("", emptyList(), params, null, kw, startLine, endLine))
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

    private fun readPackage(scope0: Scope): Scope {
        var scope = scope0
        do {
            scope = scope.getChild(read()!!, module)
        } while (consume("."))
        // a hack to get test classes proper scopes;
        // this prevents multiple main() functions in my test scopes to be confusing
        if (file.absolutePath.endsWith(scope.combinedName.replace(".", "/") + "/" + file.name)) {
            scope = scope.getChild(file.nameWithoutExtension, module)
            scope.isPseudo = true
        }
        return scope
    }

    private fun readImport() {
        /*var name = read()!!
          while (consume(".")) {
            name = "$name.${read()}"
          }
          scope.imports.add(name)*/
        do {
            read()
        } while (consume("."))
    }

    private fun readTypeAlias() {
        readType()
        if (read() != "=") throw IllegalStateException()
        readType()
    }

    private fun getChildScope(scope: Scope, name: CharSequence): Scope {
        // meh solution, abc.xyz.A$A will not work
        return if (scope.name == name && scope.isPseudo) scope
        else scope.getChild(name, module)
    }

    private fun readClass(scope: Scope, tk: CharSequence) {
        keywords.add(tk)
        val type = readType(scope)
        val child = getChildScope(scope, type.name)
        child.superTypes.addAll(type.superTypes)
        child.generics.addAll(type.generics)
        copyKeywords(child.keywords)
        if (consume("{")) {
            readClassBody(child)
        }
    }

    private fun readFunInterface(scope: Scope) {
        // read fun interface
        keywords.add("fun")
        keywords.add("interface")
        val type = readType(scope)
        val child = getChildScope(scope, type.name)
        copyKeywords(child.keywords)
        if (consume("{")) {
            readClassBody(child)
        }
    }

    private fun readGenerics(): List<Type> {
        return if (consume("<")) {
            val generics = ArrayList<Type>()
            while (!consume(">")) {
                generics.add(readType())
                consume(",")
            }
            generics
        } else emptyList()
    }

    private fun completeFunctionName(name0: CharSequence): CharSequence {
        var name = name0
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
        return name
    }

    private fun readFunctionParameters(): List<Parameter> {
        val params = ArrayList<Parameter>()
        if (!consume("(")) throw IllegalStateException("Missing opening brackets for fun in $lineNumber, $file")
        while (!consume(")")) {
            consumeAnnotations()
            val isVararg = consume("vararg")
            val isCrossInline = consume("crossinline")
            val paramName = read()!!
            if (!consume(":")) throw IllegalStateException("Missing type in $lineNumber, $file")
            val type = readType()
            params.add(Parameter(paramName, type, isVararg, isCrossInline))
            if (consume("=")) {
                skipParameterValue()
            }
            consume(",")
        }
        return params
    }

    private fun readFunction(scope: Scope, generics: List<Type>, name0: CharSequence) {
        val startLine = lineNumber
        val name = completeFunctionName(name0)
        val params = readFunctionParameters()
        var returnType: Type? = if (consume(":")) {
            readType()
        } else UnitType
        if (consume("=")) {
            returnType = scope.findMethodReturnType(name, params)
            skipEval()
        } else if (consume("{")) {
            skipBlock("{", "}")
        }
        val endLine = lineNumber
        scope.methods.add(Method(name, generics, params, returnType, copyKeywords(), startLine, endLine))
    }

    private fun readCompanion(scope: Scope) {
        if (read() != "object") throw IllegalStateException("Expected companion object")
        val hasName = !consume("{")
        val name = if (hasName) read()!! else "Companion"
        if (hasName && !consume("{")) throw IllegalStateException("Expected {")
        val child = scope.getChild(name, module)
        keywords.add("companion")
        copyKeywords(child.keywords)
        readClassBody(child)
    }

    private fun readObject(scope: Scope) {
        val name = read()!!
        val child = getChildScope(scope, name)
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

    private fun readEnumClass(scope: Scope) {
        if (!consume("class")) throw IllegalStateException("Expected class after enum")
        keywords.add("enum")
        val type = readType(scope)
        val child = getChildScope(scope, type.name)
        child.generics.addAll(type.generics)
        child.superTypes.addAll(type.superTypes)
        copyKeywords(child.keywords)
        if (consume("{")) {
            var i = 0
            enumValues@ while (!consume(";")) {
                val name = read()!!
                when {
                    name == "}" -> {
                        putBack.add("}")
                        break@enumValues
                    }

                    name.startsWith("/**") -> keywords.add(name)
                    name[0] in 'A'..'Z' || name[0] in 'a'..'z' -> readEnumValue(name, child, i++)
                    else -> throw NotImplementedError("Unknown token? $name in $file:$lineNumber,$pos")
                }
            }
            readClassBody(child)
        }
    }

    private fun readEnumValue(name: CharSequence, child: Scope, i: Int) {
        if (consume("(")) {
            // parameters
            skipBlock("(", ")")
        }
        val child1 = child.getChild(name, module)
        child1.enumOrdinal = i
        child1.superTypes.add(Type(child.name, emptyList(), emptyList(), false))
        child.childClasses.add(child1)
        copyKeywords(child1.keywords)
        if (consume("{")) {
            // custom body
            readClassBody(child1)
        }
        consume(",")
    }

    private fun skipInitBlock() {
        if (!consume("{")) throw IllegalStateException("Invalid init{} block?")
        skipBlock("{", "}")
    }

    private fun readConstructor(scope: Scope) {
        val startLine = lineNumber
        val generics = readGenerics()
        val name = ""
        val params = readConstructorParameters()
        if (consume(":")) {
            if (!(consume("this") || consume("super"))) throw IllegalStateException("Expected this or super")
            if (!consume("(")) throw IllegalStateException("Expected ( after :this/:super")
            skipBlock("(", ")")
        }
        if (consume("{")) {
            skipBlock("{", "}")
        }
        val endLine = lineNumber
        scope.methods.add(Method(name, generics, params, null, copyKeywords(), startLine, endLine))
    }

    private fun readConstructorParameters(): List<Parameter> {
        val params = ArrayList<Parameter>()
        if (!consume("(")) throw IllegalStateException("Missing opening brackets for fun")
        while (!consume(")")) {
            params.add(readConstructorParameter())
        }
        return params
    }

    private fun readConstructorParameter(): Parameter {
        consumeAnnotations()
        val isVararg = consume("vararg")
        val isCrossInline = consume("crossinline")
        val paramName = read()!!
        if (!consume(":")) throw IllegalStateException("Missing type in $lineNumber, $file")
        val type = readType()
        val param = Parameter(paramName, type, isVararg, isCrossInline)
        if (consume("=")) {
            skipParameterValue()
        }
        consume(",")
        return param
    }

    private fun readField(scope: Scope, tk: CharSequence) {
        keywords.add(tk)
        var name = read()!!
        while (consume(".")) {
            name = "$name.${read()}"
        }
        var type = if (consume(":")) readType() else scope.findFieldType(name)
        if (consume("=") || consume("by")) {
            skipEval()
        }
        var private = false
        if (consume("private")) private = true
        if (consume("get")) {
            if (private) keywords.add("private-get")
            if (read() != "(") throw IllegalStateException()
            if (read() != ")") throw IllegalStateException()
            if (consume(":")) {
                type = readType()
            }
            if (consume("{")) {
                skipBlock("{", "}")
            } else if (consume("=")) {
                skipEval()
            } else throw IllegalStateException("Expected { or = after get in $lineNumber, $file")
        }
        if (!private && consume("private")) private = true
        if (consume("set")) {
            if (private) keywords.add("private-set")
            if (consume("(")) {
                if (read() != null && read() == ")") {
                    if (consume("{")) {
                        skipBlock("{", "}")
                    } else if (consume("=")) {
                        skipEval()
                    } else throw IllegalStateException("Expected {/= after set in $lineNumber, $file")
                } else throw IllegalStateException("Expected ?){ after set in $lineNumber, $file")
            }
        }
        scope.fields.add(Field(name, type, copyKeywords()))
        if (private) keywords.add("private")
    }

    private fun readClassBody(scope0: Scope) {
        var scope = scope0
        while (true) {
            val tk = read() ?: break
            when (tk) {

                "package" -> scope = readPackage(scope0)
                "import" -> readImport()

                "typealias" -> readTypeAlias()

                "class", "interface" -> readClass(scope, tk)
                "companion" -> readCompanion(scope)
                "constructor" -> readConstructor(scope)
                "object" -> readObject(scope)
                "enum" -> readEnumClass(scope)

                "init" -> skipInitBlock()
                "val", "var" -> readField(scope, tk)
                "fun" -> {
                    // read generics
                    val generics = readGenerics()
                    val name = read()!!
                    if (name == "interface") {
                        readFunInterface(scope)
                    } else {
                        // println("reading fun $name, $keywords")
                        readFunction(scope, generics, name)
                    }
                }

                "@" -> consumeAnnotation()

                "open", "protected", "private", "public", "abstract", "override", "operator",
                "final", "inline", "reified", "const", "lateinit", "data", "annotation" -> keywords.add(tk)

                // return from this function of reading the class body
                "}" -> return

                // forgotten remains of previous thing
                "else" -> skipEval()

                else -> {
                    if (tk.startsWith("/**")) {
                        keywords.add(tk)
                    } else println("Ignored $tk in $file:$lineNumber, $pos!")
                }
            }
        }
    }

    fun index() {
        file.readText { text0, e ->
            e?.printStackTrace()
            text = (text0 ?: "") + "\n"
            readClassBody(all)
        }
    }
}