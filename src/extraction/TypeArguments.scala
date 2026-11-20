package scalex.extraction

import java.nio.file.Path
import scala.collection.mutable
import scala.meta.*

// ── Type parameter extraction (for scaffold) ────────────────────────────────

/** Extract type arguments that a class passes to a specific parent. E.g., for
  * `class Foo extends Processor[Role, String]` and parent "Processor", returns List("Role", "String").
  */
def extractParentTypeArgs(file: Path, className: String, parentName: String): List[String] = {
  if isJavaFile(file) then return Nil
  parseFile(file) match {
    case None       => Nil
    case Some(tree) =>
      val result = mutable.ListBuffer.empty[String]

      def extractFromInits(inits: List[Init]): Unit = {
        inits.foreach { init =>
          init.tpe match {
            case Type.Apply.After_4_6_0(Type.Name(name), argClause) if name == parentName =>
              argClause.values.foreach(arg => result += arg.toString())
            case Type.Apply.After_4_6_0(Type.Select(_, Type.Name(name)), argClause) if name == parentName =>
              argClause.values.foreach(arg => result += arg.toString())
            case _ =>
          }
        }
      }

      def findInTree(t: Tree): Unit = t match {
        case d: Defn.Class if d.name.value == className => extractFromInits(d.templ.inits)
        case d: Defn.Trait if d.name.value == className => extractFromInits(d.templ.inits)
        case d: Defn.Enum if d.name.value == className  => extractFromInits(d.templ.inits)
        case _                                          => t.children.foreach(findInTree)
      }

      findInTree(tree)
      result.toList
  }
}

/** Extract type parameter names from a class/trait definition. E.g., for `trait Processor[T, U]`, returns List("T",
  * "U").
  */
def extractTypeParamNames(file: Path, className: String): List[String] = {
  if isJavaFile(file) then return Nil
  parseFile(file) match {
    case None       => Nil
    case Some(tree) =>
      val result = mutable.ListBuffer.empty[String]

      def findInTree(t: Tree): Unit = t match {
        case d: Defn.Class if d.name.value == className =>
          d.tparamClause.values.foreach(p => result += p.name.value)
        case d: Defn.Trait if d.name.value == className =>
          d.tparamClause.values.foreach(p => result += p.name.value)
        case d: Defn.Enum if d.name.value == className =>
          d.tparamClause.values.foreach(p => result += p.name.value)
        case _ => t.children.foreach(findInTree)
      }

      findInTree(tree)
      result.toList
  }
}
