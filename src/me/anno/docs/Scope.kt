package me.anno.docs

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.declaredFunctions
import kotlin.reflect.full.memberProperties

class Scope(val name: String, val parent: Scope?, val module: String) {

    val superTypes = ArrayList<Type>()
    val generics = ArrayList<Type>()
    val keywords = ArrayList<CharSequence>()
    val children = HashMap<String, Scope>()
    val imports = HashSet<CharSequence>()

    val combinedName: String = if (parent == null || parent.name == "") name
    else parent.combinedName + "." + name

    val ktClass: KClass<*>? by lazy {
        try {
            if (enumOrdinal >= 0) parent?.ktClass
            else if (name == "Companion") parent!!.ktClass?.companionObject
            else this::class.java.classLoader.loadClass(combinedName).kotlin
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
            null
        }
    }

    fun findMethodReturnType(name: CharSequence, params: List<Parameter>): Type? {
        if ('.' in name) {
            // idk how to access that...
            return null
        }
        ktClass ?: return null
        val matches0 = ktClass!!.declaredFunctions.filter {
            name == it.name && it.parameters.size == params.size + 1
        }
        if (matches0.size == 1) return convertType(matches0.first().returnType)
        val matches1 = matches0.filter {
            it.parameters.withIndex().all { (idx, param) -> idx == 0 || params[idx - 1].name == param.name }
        }
        if (matches1.size == 1) return convertType(matches1.first().returnType)
        val matches2 = matches1.firstOrNull {
            it.parameters.withIndex().all { (idx, param) ->
                idx == 0 || params[idx - 1].type.name == (param.type.classifier as KClass<*>).simpleName
            }
        }
        matches2 ?: throw IllegalStateException(
            "No matching function! $combinedName.$name($params), " +
                    "candidates: ${
                        ktClass!!.declaredFunctions.filter { it.name == name }.map {
                            "${it.name}(${it.parameters.map { p -> "${p.name}: ${convertType(p.type)}" }}):${it.returnType}"
                        }
                    }"
        )
        return convertType(matches2.returnType)
    }

    private fun convertType(type: KType): Type? {
        when (val classifier = type.classifier) {
            is KClass<*> -> {
                /*println(field)
                println(clazz)
                println(field.parameters)
                println(field.annotations)
                println(type.isMarkedNullable)*/
                return Type(classifier.simpleName!!, emptyList(), emptyList(), type.isMarkedNullable)
            }

            is KTypeParameter -> {
                // ?? Generics probably...
                return Type(classifier.name, emptyList(), emptyList(), false)
            }

            else -> return null
        }
    }

    fun findFieldType(name: CharSequence): Type? {
        ktClass ?: return null
        val field = ktClass!!.memberProperties.firstOrNull { name == it.name } ?: return null
        val type = field.returnType
        return convertType(type)
    }

    fun getChild(name0: CharSequence, module: String): Scope {
        val name = name0.toString()
        return children.getOrPut(name) { Scope(name, this, module) }
    }

    val fields = ArrayList<Field>()
    val methods = ArrayList<Method>()
    var enumOrdinal = -1

    fun json(builder: StringBuilder = StringBuilder()): StringBuilder {
        builder.append("{")
        builder.append("\"p\":").append(modules.indexOf(module)).append(',')
        for ((name, child) in children.entries.sortedBy { it.value.enumOrdinal }) {
            builder.append("\"").append(name).append("\":")
            if (child.enumOrdinal >= 0 &&
                child.keywords.isEmpty() &&
                child.children.isEmpty() &&
                child.fields.isEmpty() &&
                child.methods.isEmpty() &&
                child.generics.isEmpty() &&
                child.superTypes.isEmpty()
            ) {
                builder.append(child.enumOrdinal)
            } else {
                child.json(builder)
            }
            builder.append(",")
        }
        if (enumOrdinal >= 0) {
            builder.append("\"o\":").append(enumOrdinal).append(",")
        }
        if (superTypes.isNotEmpty()) {
            builder.append("\"s\":[")
            for (type in superTypes) {
                builder.append("\"").append(type).append("\",")
            }
            builder.setLength(builder.length - 1)
            builder.append("],")
        }
        if (generics.isNotEmpty()) {
            builder.append("\"g\":[")
            for (type in generics) {
                builder.append("\"").append(type).append("\",")
            }
            builder.setLength(builder.length - 1)
            builder.append("],")
        }
        if (fields.isNotEmpty()) {
            builder.append("\"f\":[")
            for (field in fields) {
                // name, keywords, type
                builder.append("[\"").append(field.name).append("\",[")
                writeKeywordList(builder, field.keywords)
                builder.append("]")
                if (field.type != null) builder.append(",\"").append(field.type).append("\"")
                builder.append("],")
            }
            builder.setLength(builder.length - 1)
            builder.append("],")
        }
        if (methods.isNotEmpty()) {
            builder.append("\"m\":[")
            for (method in methods) {
                builder.append("[\"").append(method.name).append("\",")
                builder.append("[")
                for (param in method.params) {
                    builder.append("\"").append(param.name).append("\",")
                    builder.append("\"").append(param.type).append("\",")
                }
                if (builder.last() == ',') builder.setLength(builder.length - 1) // delete last comma
                builder.append("],[")
                writeKeywordList(builder, method.keywords)
                builder.append("],")
                if (method.returnType != null) builder.append("\"").append(method.returnType).append("\",")
                if (builder.last() == ',') builder.setLength(builder.length - 1) // delete last comma
                builder.append("],")
            }
            builder.setLength(builder.length - 1)
            builder.append("],")
        }
        if (keywords.isNotEmpty()) {
            builder.append("\"k\":[")
            writeKeywordList(builder, keywords)
            builder.append("],")
        }
        if (builder.last() == ',') builder.setLength(builder.length - 1) // delete last comma
        builder.append("}")
        return builder
    }

    fun writeKeywordList(builder: StringBuilder, keywords: List<CharSequence>) {
        for (kw in keywords) {
            val esc = if (kw.startsWith("/**")) {
                val end = if (kw.endsWith("* */")) 4
                else if (kw.endsWith("**/")) 3
                else 2
                "*" + kw.subSequence(3, kw.length - end)
                    .trim()
                    .split('\n')
                    .joinToString("\\n") { x ->
                        val y = x.trim()
                        val z = if (y.startsWith("*")) {
                            y.substring(1).trim()
                        } else y
                        z
                            .replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                    }
            } else kw
            builder.append("\"").append(esc).append("\",")
        }
        if (keywords.isNotEmpty()) builder.setLength(builder.length - 1)
    }
}
