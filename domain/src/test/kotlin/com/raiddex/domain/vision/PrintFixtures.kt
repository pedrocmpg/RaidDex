package com.raiddex.domain.vision

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.jupiter.api.Assumptions.assumeTrue

/** Prints reais ficam em prints/ (fora do git). Sem eles, os testes que dependem deles são ignorados. */
class ImageSource(private val img: BufferedImage) : PixelSource {
    override val width get() = img.width
    override val height get() = img.height
    override fun rgb(x: Int, y: Int) = img.getRGB(x, y) and 0xFFFFFF
}

object PrintFixtures {
    private val dir: File? by lazy {
        var d: File? = File("").absoluteFile
        while (d != null && !File(d, "prints").isDirectory) d = d.parentFile
        d?.let { File(it, "prints") }
    }

    fun load(name: String): PixelSource {
        val f = dir?.let { File(it, name) }
        assumeTrue(f != null && f.exists(), "print ausente (prints/$name); teste ignorado")
        return ImageSource(ImageIO.read(f!!))
    }
}
