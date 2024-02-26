package me.anno.docs.utils

import me.anno.image.raw.ByteImage
import me.anno.io.files.Reference.getReference
import me.anno.utils.OS.desktop
import me.anno.utils.types.InputStreams.readNBytes2

/**
 * This was a test on whether I find the sources for the icons in IntelliJ IDEA.
 * I was able to reverse-engineer the db-files, but failed to find the correct icons.
 * */
fun main() {
    desktop.getChild("icon").tryMkdirs()
    val folder = getReference("C:\\Program Files\\IntelliJ IDEA\\bin\\icons")
    for (src in folder.listChildren()) {
        var i = 0
        val stream = src.inputStreamSync()
        var pos = 0L
        while (true) {
            val dim = stream.read()
            if (dim <= 0) break
            if (dim == 254) {
                val w = stream.read()
                var h = stream.read()
                if (h < 8) {
                    println("Weird! $w x $h @${pos.toString(16)}")
                    pos++
                    h = stream.read()
                }
                // println("$w x $h")
                val size = w * h * 4
                val bytes = stream.readNBytes2(size, true)
                val image = ByteImage(w, h, ByteImage.Format.BGRA, bytes)
                image.write(desktop.getChild("icon/${src.nameWithoutExtension}-$i.png"))
                pos += size + 2
            } else {
                val size = dim * dim * 4
                val bytes = stream.readNBytes2(size, true)
                pos += size + 1
                val image = ByteImage(dim, dim, ByteImage.Format.BGRA, bytes)
                image.write(desktop.getChild("icon/${src.nameWithoutExtension}-$i.png"))
            }
            i++
        }
        stream.close()
    }
}