package mlscript.compiler.backend.wasm
import mlscript.codegen.CodeGenError
import scala.collection.mutable.Map as MutMap
import scala.sys.process._
import java.io._

object Env {
  sealed trait OS
  object Linux extends OS
  object Windows extends OS
  object Mac extends OS

  lazy val os = {
    // If all fails returns Linux
    val optOsName = Option(System.getProperty("os.name"))
    optOsName.map(_.toLowerCase()).map { osName =>
      if (osName contains "linux") Linux
      else if (osName contains "win") Windows
      else if (osName contains "mac") Mac
      else Linux
    } getOrElse Linux
  }
}

object CodePrinter {
  private var counterMap = MutMap.empty[String,Int]

  def apply(m: Module) =
    val outDirName = "compiler/shared/test/diff/wasmout"
    val count = counterMap.getOrElse(m.name,1)
    counterMap(m.name) = count + 1
    def pathWithExt(ext: String) = s"$outDirName/${nameWithExt(ext)}"
    def nameWithExt(ext: String) = s"${m.name}_$count.$ext"

    val (local, inPath) =
      import Env._
      os match {
        case Linux   => ("./bin/linux/wat2wasm", "wat2wasm")
        case Windows => ("./bin/windows/wat2wasm.exe", "wat2wasm.exe")
        case Mac     => ("./bin/macos/wat2wasm", "wat2wasm")
      }

    val w2wOptions = s"${pathWithExt("wat")} -o ${pathWithExt("wasm")}"

    val outDir = new File(outDirName)
    if (!outDir.exists())
      outDir.mkdir()

    m.writeWasmText(pathWithExt("wat"))

    try {
      try {
        s"$local $w2wOptions".!!
      } catch {
        case _: IOException =>
          s"$inPath $w2wOptions".!!
      }
    } catch {
      case _: IOException =>
        throw CodeGenError(
          "wat2wasm utility was not found under ./bin or in system path, " +
            "or did not have permission to execute"
        )
      case _: RuntimeException =>
        throw CodeGenError(
          s"wat2wasm failed to translate WebAssembly text file ${pathWithExt("wat")} to binary"
        )
    }
}