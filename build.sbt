import sbtcrossproject.CrossPlugin.autoImport.*

ThisBuild / scalaVersion := "3.3.7"

val CatsEffectV = "3.6.3"
val CirceV = "0.14.8"
val DoobieV = "1.0.0-RC12"
val SkunkV = "0.6.5"
val ScalaJavaTimeV = "2.6.0"
val JsoniterV = "2.30.2"
val WeaverV = "0.11.3"

lazy val root = (project in file("."))
  .aggregate(
    core.jvm,
    core.js,
    core.native,
    circe.jvm,
    circe.js,
    circe.native,
    jsoniter.jvm,
    doobie,
    skunk
  )
  .settings(
    publish / skip := true
  )

lazy val integration = (project in file("it"))
  .dependsOn(doobie, skunk, circe.jvm)
  .settings(
    name := "pgmq4s-it",
    publish / skip := true,
    libraryDependencies ++= Seq(
      "org.typelevel" %% "weaver-cats" % WeaverV % Test,
      "org.tpolecat" %% "doobie-hikari" % DoobieV % Test,
      "org.tpolecat" %% "skunk-core" % SkunkV % Test
    ),
    Test / parallelExecution := false
  )

lazy val core = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "pgmq4s-core",
    libraryDependencies += "org.typelevel" %%% "cats-effect" % CatsEffectV,
    libraryDependencies += "org.typelevel" %%% "weaver-cats" % WeaverV % Test
  )
  .jsSettings(
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % ScalaJavaTimeV % Test
  )

// === DATABASE ===
lazy val doobie = (project in file("module/database/doobie"))
  .dependsOn(core.jvm)
  .settings(
    name := "pgmq4s-doobie",
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "doobie-core" % DoobieV,
      "org.tpolecat" %% "doobie-postgres" % DoobieV
    )
  )

lazy val skunk = (project in file("module/database/skunk"))
  .dependsOn(core.jvm)
  .settings(
    name := "pgmq4s-skunk",
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "skunk-core" % SkunkV,
      "org.typelevel" %% "weaver-cats" % WeaverV % Test
    )
  )

// === JSON ===
lazy val circe = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("module/json/circe"))
  .dependsOn(core)
  .settings(
    name := "pgmq4s-circe",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core" % CirceV,
      "io.circe" %%% "circe-parser" % CirceV
    ),
    libraryDependencies += "org.typelevel" %%% "weaver-cats" % WeaverV % Test
  )

lazy val jsoniter = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("module/json/jsoniter"))
  .dependsOn(core)
  .settings(
    name := "pgmq4s-jsoniter",
    libraryDependencies ++= Seq(
      "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-core" % JsoniterV,
      "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-macros" % JsoniterV % Provided
    ),
    libraryDependencies += "org.typelevel" %%% "weaver-cats" % WeaverV % Test
  )
