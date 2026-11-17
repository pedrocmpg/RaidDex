package com.raiddex.domain

import java.io.File

/** Carrega o gamedata.json empacotado no app (caminho relativo à raiz do repositório). */
object TestData {
    val data: GameData by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "app/src/main/assets/gamedata.json").exists()) dir = dir.parentFile
        GameData.parse(File(requireNotNull(dir) { "gamedata.json não encontrado" }, "app/src/main/assets/gamedata.json").readText())
    }
}
