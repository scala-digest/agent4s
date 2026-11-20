package scalex

abstract class Agent4sTestBase extends ScalexTestBase {
  override def beforeAll(): Unit = {
    super.beforeAll()
    // Bug hunt test fixtures
    writeFile(
      "src/main/scala/com/bugs/OptionGetBug.scala",
      """package com.bugs
        |
        |object OptionGetBug {
        |  def bad: String = Option.empty[String].get
        |  def ok: String = Option.empty[String].getOrElse("default")
        |  def mapGet: Option[String] = Map("a" -> "b").get("a")
        |}
        |""".stripMargin
    )

    writeFile(
      "src/main/scala/com/bugs/CollectionBug.scala",
      """package com.bugs
        |
        |object CollectionBug {
        |  def badHead: Int = List.empty[Int].head
        |  def badLast: Int = List(1, 2).last
        |  def ok: Int = List(1, 2).headOption.getOrElse(0)
        |}
        |""".stripMargin
    )

    writeFile(
      "src/main/scala/com/bugs/NullBug.scala",
      """package com.bugs
        |
        |object NullBug {
        |  val bad: String = null
        |  val ok: String = ""
        |}
        |""".stripMargin
    )

    writeFile(
      "src/main/scala/com/bugs/CastBug.scala",
      """package com.bugs
        |
        |object CastBug {
        |  def bad(x: Any): String = x.asInstanceOf[String]
        |  def ok(x: Any): Boolean = x.isInstanceOf[String]
        |}
        |""".stripMargin
    )

    writeFile(
      "src/main/scala/com/bugs/ResourceBug.scala",
      """package com.bugs
        |
        |import scala.io.Source
        |
        |object ResourceBug {
        |  def bad: String = Source.fromFile("test.txt").mkString
        |  def ok: String = {
        |    val src = Source.fromFile("test.txt")
        |    try src.mkString finally src.close()
        |  }
        |}
        |""".stripMargin
    )

    writeFile(
      "src/main/scala/com/bugs/SecretBug.scala",
      """package com.bugs
        |
        |object SecretBug {
        |  val password = "hunter2"
        |  val apiKey = "fixture"
        |  val safeName = "not-a-secret"
        |  val placeholder = "changeme"
        |}
        |""".stripMargin
    )

    // Taint analysis test fixtures
    writeFile(
      "src/main/scala/com/bugs/TaintSafe.scala",
      """package com.bugs
        |
        |object TaintSafe {
        |  val table = "users"
        |  def safe = Fragment.const(s"SELECT * FROM $table")
        |}
        |""".stripMargin
    )

    writeFile(
      "src/main/scala/com/bugs/TaintDangerous.scala",
      """package com.bugs
        |
        |object TaintDangerous {
        |  def handler(request: Request) = {
        |    val id = request.params("id")
        |    Fragment.const(s"SELECT * FROM users WHERE id = $id")
        |  }
        |}
        |""".stripMargin
    )

    run("git", "add", ".")
    run("git", "commit", "-m", "agent4s fixtures")
  }
}
