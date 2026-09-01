ThisBuild / tlBaseVersion := "0.14"
ThisBuild / scalaVersion := "3.3.8"
ThisBuild / tlJdkRelease := Some(17)
ThisBuild / organization := "io.github.matejcerny"
ThisBuild / organizationName := "Matej Cerny"
ThisBuild / startYear := Some(2026)
ThisBuild / licenses := Seq(License.MIT)
ThisBuild / developers := List(tlGitHubDev("matejcerny", "Matej Cerny"))

// === CI/CD WORKFLOWS ===
val Java17 = JavaSpec.temurin("17")
val Java25 = JavaSpec.temurin("25")

// The first version builds published artifacts. Kyo requires JDK 25; JDK 17 verifies all other JVM modules.
ThisBuild / githubWorkflowJavaVersions := Seq(Java25, Java17)
ThisBuild / githubWorkflowBuildMatrixAdditions ~= { matrix =>
  matrix.updated("project", matrix("project") :+ "jdk17JVM")
}
ThisBuild / githubWorkflowBuildMatrixExclusions ++= Seq(
  MatrixExclude(Map("project" -> "rootJVM", "java" -> Java17.render)),
  MatrixExclude(Map("project" -> "jdk17JVM", "java" -> Java25.render))
)
ThisBuild / githubWorkflowBuildPreamble ++= Seq(
  WorkflowStep.Run(
    name = Some("Install native dependencies"),
    cond = Some("matrix.project == 'rootNative'"),
    commands = List("sudo apt-get install -y libutf8proc-dev")
  )
)

ThisBuild / githubWorkflowBuildPostamble ++= Seq(
  WorkflowStep.Run(
    name = Some("Start Postgres for integration tests"),
    cond = Some(
      "matrix.project == 'rootJVM' || matrix.project == 'jdk17JVM' || matrix.project == 'rootNative'"
    ),
    commands = List(
      "docker compose up -d postgres",
      "for i in {1..30}; do docker compose exec -T postgres pg_isready -U pgmq && break; sleep 2; done",
      "docker compose exec -T postgres psql -U pgmq -d pgmq -c \"CREATE EXTENSION IF NOT EXISTS pgmq;\""
    )
  ),
  WorkflowStep.Run(
    name = Some("Run JVM integration tests on JDK 17"),
    cond = Some("matrix.project == 'jdk17JVM'"),
    commands = List("sbt integrationJVM/test")
  ),
  WorkflowStep.Run(
    name = Some("Run coverage"),
    cond = Some("matrix.project == 'rootJVM'"),
    commands = List("sbt clean coverage rootJVM/test integrationJVM/test kyoIntegration/test rootJVM/coverageAggregate")
  ),
  WorkflowStep.Use(
    UseRef.Public("codecov", "codecov-action", "v5"),
    name = Some("Upload coverage to Codecov"),
    cond = Some("matrix.project == 'rootJVM'"),
    params = Map("token" -> "${{ secrets.CODECOV_TOKEN }}")
  ),
  WorkflowStep.Run(
    name = Some("Run Native integration tests"),
    cond = Some("matrix.project == 'rootNative'"),
    commands = List("sbt integrationNative/test")
  )
)

// === VERSIONS ===
val AnormV = "3.1.0"
val CatsV = "2.13.0"
val CatsEffectV = "3.7.1"
val CirceV = "0.14.16"
val DoobieV = "1.0.0-RC13"
val Fs2V = "3.13.0"
val SkunkV = "1.0.0"
val ScalaJavaTimeV = "2.7.0"
val JsoniterV = "2.40.1"
val KyoV = "1.0.0-RC6"
val KyoScalaV = "3.8.4"
val KyoJdkV = 25
val PostgresV = "42.7.10"
val PlayJsonV = "3.0.6"
val SlickV = "3.6.1"
val SprayJsonV = "1.3.6"
val UpickleV = "4.4.3"
val WeaverV = "0.13.0"

lazy val root = tlCrossRootProject
  .settings(name := "pgmq4s")
  .aggregate(
    core,
    cats,
    stream,
    circe,
    jsoniter,
    playJson,
    sprayJson,
    upickle,
    anorm,
    doobie,
    skunk,
    slick,
    kyo
  )

lazy val jdk17JVM = (project in file("target/jdk17-jvm"))
  .aggregate(
    core.jvm,
    cats.jvm,
    stream.jvm,
    circe.jvm,
    jsoniter.jvm,
    playJson,
    sprayJson,
    upickle.jvm,
    anorm,
    doobie,
    skunk.jvm,
    slick
  )
  .settings(
    name := "pgmq4s-jdk17-ci",
    publish / skip := true,
    mimaPreviousArtifacts := Set.empty
  )

lazy val core = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "pgmq4s-core",
    libraryDependencies += "org.typelevel" %%% "cats-effect" % CatsEffectV % Test,
    libraryDependencies += "org.typelevel" %%% "weaver-cats" % WeaverV % Test,
    libraryDependencies += "org.typelevel" %%% "weaver-scalacheck" % WeaverV % Test
  )
  .jvmSettings(
    // sbt-typelevel sets -project to the module name; replace with the top-level project name
    Compile / doc / scalacOptions ~= (_.map { case "pgmq4s-core" => "pgmq4s"; case other => other }),
    Compile / doc / scalacOptions ++= Seq(
      "-siteroot",
      ((ThisBuild / baseDirectory).value / "docs").getAbsolutePath,
      "-social-links:github::https://github.com/matejcerny/pgmq4s",
      "-project-logo", "docs/_assets/images/logo.png",
      "-project-footer",
      "Copyright Matej Cerny",
      "-versions-dictionary-url",
      "https://matejcerny.github.io/pgmq4s/versions.json",
      "-snippet-compiler:nocompile"
    ),
    Compile / doc := {
      val output = (Compile / doc).value
      val assetsDir = (ThisBuild / baseDirectory).value / "docs" / "_assets"
      val favicon = assetsDir / "images" / "favicon.ico"
      if (favicon.exists()) IO.copyFile(favicon, output / "favicon.ico")
      val customCss = assetsDir / "css" / "custom.css"
      if (customCss.exists()) IO.copyFile(customCss, output / "styles" / "staticsitestyles.css")
      output
    }
  )
  .jsSettings(
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % ScalaJavaTimeV
  )

lazy val cats = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("module/cats"))
  .dependsOn(core)
  .settings(
    name := "pgmq4s-cats",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % CatsV,
      "org.typelevel" %%% "cats-effect" % CatsEffectV % Test,
      "org.typelevel" %%% "weaver-cats" % WeaverV % Test
    ),
    mimaPreviousArtifacts := Set.empty
  )

lazy val stream = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("module/stream"))
  .dependsOn(core, cats % "test->compile")
  .settings(
    name := "pgmq4s-stream",
    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-core" % Fs2V,
      "org.typelevel" %%% "cats-effect" % CatsEffectV,
      "org.typelevel" %%% "weaver-cats" % WeaverV % Test
    ),
    mimaPreviousArtifacts := Set.empty
  )

// === DATABASE ===
lazy val anorm = (project in file("module/database/anorm"))
  .dependsOn(core.jvm)
  .settings(
    name := "pgmq4s-anorm",
    libraryDependencies ++= Seq(
      "org.playframework.anorm" %% "anorm" % AnormV,
      "org.typelevel" %% "weaver-cats" % WeaverV % Test
    )
  )

lazy val doobie = (project in file("module/database/doobie"))
  .dependsOn(cats.jvm)
  .settings(
    name := "pgmq4s-doobie",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % CatsEffectV,
      "org.typelevel" %% "doobie-core" % DoobieV,
      "org.typelevel" %% "doobie-postgres" % DoobieV
    )
  )

lazy val skunk = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("module/database/skunk"))
  .dependsOn(cats)
  .settings(
    name := "pgmq4s-skunk",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-effect" % CatsEffectV,
      "org.tpolecat" %%% "skunk-core" % SkunkV,
      "org.typelevel" %%% "weaver-cats" % WeaverV % Test
    )
  )

lazy val slick = (project in file("module/database/slick"))
  .dependsOn(core.jvm)
  .settings(
    name := "pgmq4s-slick",
    libraryDependencies += "com.typesafe.slick" %% "slick" % SlickV
  )

lazy val kyo = (project in file("module/database/kyo"))
  .dependsOn(core.jvm)
  .settings(
    name := "pgmq4s-kyo",
    scalaVersion := KyoScalaV,
    tlJdkRelease := Some(KyoJdkV),
    libraryDependencies ++= Seq(
      "io.getkyo" %% "kyo-sql" % KyoV,
      "io.getkyo" %% "kyo-sql-postgres" % KyoV
    ),
    mimaPreviousArtifacts := Set.empty
  )

// === JSON ===
lazy val circe = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("module/json/circe"))
  .dependsOn(core % "compile->compile;test->test")
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
  .dependsOn(core % "compile->compile;test->test")
  .settings(
    name := "pgmq4s-jsoniter",
    libraryDependencies ++= Seq(
      "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-core" % JsoniterV,
      "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-macros" % JsoniterV % Provided
    ),
    libraryDependencies += "org.typelevel" %%% "weaver-cats" % WeaverV % Test
  )

lazy val upickle = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("module/json/upickle"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(
    name := "pgmq4s-upickle",
    libraryDependencies += "com.lihaoyi" %%% "upickle" % UpickleV,
    libraryDependencies += "org.typelevel" %%% "weaver-cats" % WeaverV % Test
  )

lazy val playJson = (project in file("module/json/play-json"))
  .dependsOn(core.jvm % "compile->compile;test->test")
  .settings(
    name := "pgmq4s-play-json",
    libraryDependencies += "org.playframework" %% "play-json" % PlayJsonV,
    libraryDependencies += "org.typelevel" %% "weaver-cats" % WeaverV % Test
  )

lazy val sprayJson = (project in file("module/json/spray-json"))
  .dependsOn(core.jvm % "compile->compile;test->test")
  .settings(
    name := "pgmq4s-spray-json",
    libraryDependencies += "io.spray" %% "spray-json" % SprayJsonV,
    libraryDependencies += "org.typelevel" %% "weaver-cats" % WeaverV % Test
  )

// === Integration tests ===

lazy val integration = crossProject(JVMPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .in(file("it"))
  .dependsOn(skunk, circe, stream)
  .jvmConfigure(_.dependsOn(anorm, doobie, slick))
  .settings(
    name := "pgmq4s-it",
    publish / skip := true,
    libraryDependencies += "org.typelevel" %%% "weaver-cats" % WeaverV % Test,
    Test / parallelExecution := false
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "doobie-hikari" % DoobieV % Test,
      "com.typesafe.slick" %% "slick-hikaricp" % SlickV % Test,
      "org.postgresql" % "postgresql" % PostgresV % Test
    )
  )

lazy val kyoIntegration = (project in file("it-kyo"))
  .dependsOn(kyo)
  .settings(
    name := "pgmq4s-it-kyo",
    publish / skip := true,
    scalaVersion := KyoScalaV,
    tlJdkRelease := Some(KyoJdkV),
    libraryDependencies ++= Seq(
      "io.getkyo" %% "kyo-test-api" % KyoV % Test,
      "io.getkyo" %% "kyo-test-runner" % KyoV % Test
    ),
    testFrameworks += new TestFramework("kyo.test.runner.SbtFramework"),
    Test / parallelExecution := false,
    mimaPreviousArtifacts := Set.empty
  )

// === Examples ===

lazy val examples = (project in file("examples"))
  .dependsOn(core.jvm, circe.jvm, anorm, doobie, skunk.jvm, slick, kyo)
  .disablePlugins(HeaderPlugin)
  .settings(
    name := "pgmq4s-examples",
    publish / skip := true,
    scalaVersion := KyoScalaV,
    tlJdkRelease := Some(KyoJdkV),
    mimaPreviousArtifacts := Set.empty,
    coverageEnabled := false,
    libraryDependencies ++= Seq(
      "org.typelevel" %% "doobie-hikari" % DoobieV
    )
  )
