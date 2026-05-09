package j2k.runner

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.j2k.ConverterSettings
import org.jetbrains.kotlin.nj2k.NewJavaToKotlinConverter
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.system.exitProcess

@Suppress("UnstableApiUsage", "OVERRIDE_DEPRECATION")
class J2KStarter : ApplicationStarter {
    override val commandName: String = "j2k"

    override fun main(args: List<String>) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                runConversion(args)
                exitProcess(0)
            } catch (error: Throwable) {
                System.err.println("[j2k] fatal: ${error.javaClass.simpleName}: ${error.message}")
                error.printStackTrace(System.err)
                exitProcess(1)
            }
        }
    }

    private fun runConversion(args: List<String>) {
        if (args.size < 3) error("usage: j2k <input-dir> <output-dir>")
        val input = Path.of(args[1]).toAbsolutePath().normalize()
        val output = Path.of(args[2]).toAbsolutePath().normalize()
        require(input.exists()) { "input dir does not exist: $input" }
        output.createDirectories()

        val workRoot = Files.createTempDirectory("j2k-work-")
        val srcRoot = workRoot.resolve("src")
        srcRoot.createDirectories()
        copyJavaTree(input, srcRoot)

        val javaPaths = collectJavaFiles(srcRoot)
        if (javaPaths.isEmpty()) error("no .java files under $input")
        log("staged ${javaPaths.size} java files")

        val project = ProjectManagerEx.getInstanceEx().openProject(
            workRoot,
            OpenProjectTask {
                forceOpenInNewFrame = false
                isNewProject = true
                useDefaultProjectAsTemplate = false
                runConfigurators = false
            },
        ) ?: error("could not open project at $workRoot")

        var jdk: JdkRegistration? = null
        try {
            jdk = attachJdkAndSource(project, srcRoot)
            waitForSmartMode(project)
            val converted = convert(project, srcRoot)
            mirrorOutputs(converted, srcRoot, output)
            if (converted.size < javaPaths.size) {
                error("J2K returned no output for ${javaPaths.size - converted.size} of ${javaPaths.size} file(s)")
            }
        } finally {
            ApplicationManager.getApplication().invokeAndWait {
                if (jdk?.added == true) {
                    runWriteAction {
                        ProjectJdkTable.getInstance().removeJdk(jdk.sdk)
                    }
                }
                ProjectManagerEx.getInstanceEx().closeAndDispose(project)
            }
        }
    }

    private fun copyJavaTree(from: Path, to: Path) {
        Files.walk(from).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".java") }
                .forEach { src ->
                    val dst = to.resolve(from.relativize(src).toString())
                    dst.parent.createDirectories()
                    Files.copy(src, dst)
                }
        }
    }

    private fun collectJavaFiles(root: Path): List<Path> =
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.toString().endsWith(".java") }.toList()
        }

    private data class JdkRegistration(val sdk: Sdk, val added: Boolean)

    private fun attachJdkAndSource(project: Project, srcRoot: Path): JdkRegistration {
        val registration = edtWrite {
            val table = ProjectJdkTable.getInstance()
            val existing = table.findJdk("auto-jdk-21")
            if (existing != null) {
                JdkRegistration(existing, added = false)
            } else {
                val sdk = JavaSdk.getInstance().createJdk("auto-jdk-21", System.getProperty("java.home"), false)
                table.addJdk(sdk)
                JdkRegistration(sdk, added = true)
            }
        }
        edtWrite {
            ProjectRootManager.getInstance(project).projectSdk = registration.sdk
        }

        val module = ensureModule(project, srcRoot)
        val srcVf = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(srcRoot)
            ?: error("no VFS entry for $srcRoot")

        edtWrite {
            val rootModel: ModifiableRootModel = ModuleRootManager.getInstance(module).modifiableModel
            rootModel.contentEntries.forEach { rootModel.removeContentEntry(it) }
            val entry = rootModel.addContentEntry(srcVf)
            entry.addSourceFolder(srcVf, false)
            rootModel.inheritSdk()
            rootModel.commit()
        }
        VfsUtil.markDirtyAndRefresh(false, true, true, srcVf)
        return registration
    }

    private fun ensureModule(project: Project, srcRoot: Path): Module {
        ModuleManager.getInstance(project).modules.firstOrNull()?.let { return it }
        return edtWrite {
            val model: ModifiableModuleModel = ModuleManager.getInstance(project).getModifiableModel()
            val module = model.newModule(srcRoot.parent.resolve("module.iml").toString(), "JAVA_MODULE")
            model.commit()
            module
        }
    }

    private fun waitForSmartMode(project: Project) {
        val dumb = DumbService.getInstance(project)
        val timeoutMs = System.getenv("J2K_INDEX_TIMEOUT_MS")?.toLongOrNull() ?: 30L * 60L * 1000L
        val deadline = System.currentTimeMillis() + timeoutMs
        while (dumb.isDumb && System.currentTimeMillis() < deadline) {
            Thread.sleep(250)
        }
        if (dumb.isDumb) error("indexing did not complete within ${timeoutMs / 1000}s")
    }

    private fun convert(project: Project, srcRoot: Path): Map<Path, String> {
        val files = ApplicationManager.getApplication().runReadAction<List<PsiJavaFile>> {
            val psi = PsiManager.getInstance(project)
            val vfs = LocalFileSystem.getInstance()
            collectJavaFiles(srcRoot)
                .mapNotNull { vfs.refreshAndFindFileByNioFile(it) }
                .mapNotNull { psi.findFile(it) as? PsiJavaFile }
        }
        edt {
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        val module = edt { ModuleManager.getInstance(project).modules.first() }
        val converter = NewJavaToKotlinConverter(project, module, ConverterSettings.defaultSettings)
        val result = ApplicationManager.getApplication()
            .executeOnPooledThread<org.jetbrains.kotlin.j2k.Result> {
                ApplicationManager.getApplication().runReadAction<org.jetbrains.kotlin.j2k.Result> {
                    converter.elementsToKotlin(files)
                }
            }
            .get()

        return files.zip(result.results).mapNotNull { (java, conversion) ->
            val text = conversion?.text ?: return@mapNotNull null
            java.virtualFile.toNioPath().toAbsolutePath().normalize() to stripMarkers(text)
        }.toMap()
    }

    private fun mirrorOutputs(converted: Map<Path, String>, srcRoot: Path, output: Path) {
        for ((javaPath, text) in converted) {
            val relative = srcRoot.relativize(javaPath).toString().removeSuffix(".java")
            val target = output.resolve("$relative.kt")
            target.parent.createDirectories()
            target.writeText(text)
        }
        log("wrote ${converted.size} Kotlin files to $output")
    }

    private fun stripMarkers(text: String): String =
        text.replace(Regex("""/\*@@[a-z]+@@\*/"""), "")
            .replace(Regex("""\bkotlin\.(Int|Long|Short|Byte|Float|Double|Boolean|Char|String|Unit|Any)\b""")) {
                it.groupValues[1]
            }

    private fun <T> edt(block: () -> T): T {
        var value: T? = null
        var failure: Throwable? = null
        ApplicationManager.getApplication().invokeAndWait {
            try {
                value = block()
            } catch (error: Throwable) {
                failure = error
            }
        }
        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private fun <T> edtWrite(block: () -> T): T =
        edt { runWriteAction { block() } }

    private fun log(message: String) = println("[j2k] $message")
}
