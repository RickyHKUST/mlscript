package mlscript.compiler.backend.wasm

import mlscript.compiler.backend.wasm.WasmInstructions._
import mlscript.compiler.backend.wasm.WasmInstructions
import mlscript.compiler.backend.wasm.Document._
import scala.language.implicitConversions

// Printer for Wasm modules
object ModulePrinter {
  private implicit def s2d(s: String): Raw = Raw(s)

  private def mkMod(mod: Module): Document = stack(
    "(module ",
    Indented(Stacked(mod.imports map mkImport)),
    Indented("(global (mut i32) i32.const 0) " * mod.globals),
    Indented(Stacked(mod.functions map mkFun)),
    ")"
  )

  private def mkImport(s: String): Document =
    line(List("(import ", s, ")"))

  private def mkFun(fh: Function): Document = {
    val name = fh.name
    val isMain = fh.isMain
    val exportDoc: Document = if (isMain) s"""(export "$name" (func $$$name))""" else ""
    val paramsDoc: Document = if (fh.args == 0) "" else {
      line(List(
        "(param ",
        Lined(List.fill(fh.args)(Raw("i32")), " "),
        ") "
      ))
    }
    val resultDoc: Document = if (isMain) "" else "(result i32) "
    val localsDoc: Document =
      if (fh.locals > 0)
        "(local " <:> Lined(List.fill(fh.locals)(Raw("i32")), " ") <:> ")"
      else
        ""

    stack(
      exportDoc,
      line(List(s"(func $$${fh.name} ", paramsDoc, resultDoc, localsDoc)),
      Indented(Stacked(mkCode(fh.code))),
      ")"
    )
  }

  private def mkCode(code: Code): List[Document] = code.instructions match {
    case Nil => Nil
    case h :: t => h match {
      case Else =>
        Unindented(mkInstr(h)) ::
        mkCode(t)
      case End =>
        Unindented(mkInstr(h)) ::
        (mkCode(t) map Unindented.apply)
      case If_void | If_i32 | Block(_) | Loop(_) =>
        mkInstr(h) ::
        (mkCode(t) map Indented.apply)
      case _ =>
        mkInstr(h) ::
        mkCode(t)
    }
  }

  private def mkInstr(instr: WasmInstruction): Document = {
    instr match {
      case I32Const(value) => s"i32.const $value"
      case F32Const(value) => s"f32.const $value"
      case Add => "i32.add"
      case Sub => "i32.sub"
      case Mul => "i32.mul"
      case Div => "i32.div_s"
      case Rem => "i32.rem_s"
      case And => "i32.and"
      case Or  => "i32.or"
      case Eqz => "i32.eqz"
      case Lt_s => "i32.lt_s"
      case Le_s => "i32.le_s"
      case Eq => "i32.eq"
      case Drop => "drop"
      case If_void => "if"
      case If_i32 => "if (result i32)"
      case Else => "else"
      case Block(label) => s"block $$${label.getOrElse("")}"
      case Loop(label) => s"loop $$${label.getOrElse("")}"
      case Br(label)=> s"br $label"
      case Return => "ret"
      case End => "end"
      case Call(name) => s"call $$$name"
      case Unreachable => "unreachable"
      case GetLocal(index) => s"local.get $index"
      case SetLocal(index) => s"local.set $index"
      case GetGlobal(index) => s"global.get $index"
      case SetGlobal(index) => s"global.set $index"
      case Store => "i32.store"
      case Load => "i32.load"
      case Store8 => "i32.store8"
      case Load8_u => "i32.load8_u"
      case Comment(s) =>
        Stacked(s.split('\n').toList.map(s => Raw(s";; $s")))
    }
  }

  def apply(mod: Module) = mkMod(mod).print
  def apply(fh: Function) = mkFun(fh).print
  def apply(instr: WasmInstruction) = mkInstr(instr).print

}