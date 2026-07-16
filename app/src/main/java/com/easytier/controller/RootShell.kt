package com.easytier.controller

/**
 * 执行 Root Shell 命令的工具类
 * 通过 Runtime.exec("su") 获取 root 权限
 */
object RootShell {

    private const val MODULE_DIR = "/data/adb/modules/easytier_pro"
    private const val CONFIG_DIR = "/data/adb/easytier_pro"
    private const val BIN_DIR = "$MODULE_DIR/bin"

    data class Result(
        val success: Boolean,
        val output: String,
        val error: String
    )

    fun exec(command: String, timeoutMs: Long = 10000L): Result {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val outStream = process.inputStream.bufferedReader()
            val errStream = process.errorStream.bufferedReader()

            val output = StringBuilder()
            val error = StringBuilder()

            val outThread = Thread {
                outStream.forEachLine { output.appendLine(it) }
            }
            val errThread = Thread {
                errStream.forEachLine { error.appendLine(it) }
            }
            outThread.start()
            errThread.start()

            val finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return Result(false, output.toString(), "Timeout after ${timeoutMs}ms")
            }
            outThread.join(2000)
            errThread.join(2000)

            Result(process.exitValue() == 0, output.toString().trim(), error.toString().trim())
        } catch (e: Exception) {
            Result(false, "", e.message ?: "Unknown error")
        }
    }

    fun hasRoot(): Boolean {
        return try {
            val r = exec("id", 5000)
            r.success && r.output.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }

    fun isModuleInstalled(): Boolean {
        return try {
            exec("test -d $MODULE_DIR && echo yes", 5000).output.contains("yes")
        } catch (_: Exception) {
            false
        }
    }

    fun getModuleDir(): String = MODULE_DIR
    fun getConfigDir(): String = CONFIG_DIR
    fun getBinDir(): String = BIN_DIR
}