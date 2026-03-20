ThisBuild / tlBaseVersion := "0.5"
ThisBuild / scalaVersion := "3.3.7"
ThisBuild / organization := "io.github.matejcerny"
ThisBuild / organizationName := "Matej Cerny"
ThisBuild / startYear := Some(2026)
ThisBuild / licenses := Seq(License.MIT)
ThisBuild / developers := List(tlGitHubDev("matejcerny", "Matej Cerny"))

// === CI/CD WORKFLOWS ===
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
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
    cond = Some("matrix.project == 'rootJVM' || matrix.project == 'rootNative'"),
    commands = List(
      "docker compose up -d postgres",
      "for i in {1..30}; do docker compose exec -T postgres pg_isready -U pgmq && break; sleep 2; done",
      "docker compose exec -T postgres psql -U pgmq -d pgmq -c \"CREATE EXTENSION IF NOT EXISTS pgmq;\""
    )
  ),
  WorkflowStep.Run(
    name = Some("Run coverage"),
    cond = Some("matrix.project == 'rootJVM'"),
    commands = List("sbt clean coverage rootJVM/test integrationJVM/test rootJVM/coverageAggregate")
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
val AnormV = "2.11.0"
val CatsEffectV = "3.6.3"
val CirceV = "0.14.8"
val DoobieV = "1.0.0-RC12"
val SkunkV = "0.6.5"
val ScalaJavaTimeV = "2.6.0"
val JsoniterV = "2.30.2"
val PostgresV = "42.7.5"
val PlayJsonV = "3.0.4"
val SlickV = "3.6.1"
val SprayJsonV = "1.3.6"
val UpickleV = "3.2.0"
val WeaverV = "0.11.3"

lazy val root = tlCrossRootProject
  .settings(name := "pgmq4s")
  .aggregate(core, circe, jsoniter, playJson, sprayJson, upickle, anorm, doobie, skunk, slick)

lazy val integration = crossProject(JVMPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .in(file("it"))
  .dependsOn(skunk, circe)
  .jvmConfigure(_.dependsOn(anorm, doobie, slick))
  .settings(
    name := "pgmq4s-it",
    publish / skip := true,
    libraryDependencies += "org.typelevel" %%% "weaver-cats" % WeaverV % Test,
    Test / parallelExecution := false
  )
  .jvmSettings(
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "doobie-hikari" % DoobieV % Test,
      "com.typesafe.slick" %% "slick-hikaricp" % SlickV % Test,
      "org.postgresql" % "postgresql" % PostgresV % Test
    )
  )

lazy val core = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "pgmq4s-core",
    libraryDependencies += "org.typelevel" %%% "cats-effect" % CatsEffectV,
    libraryDependencies += "org.typelevel" %%% "weaver-cats" % WeaverV % Test,
    mimaPreviousArtifacts := Set.empty
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
    libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % ScalaJavaTimeV % Test
  )

// === DATABASE ===
lazy val anorm = (project in file("module/database/anorm"))
  .dependsOn(core.jvm)
  .settings(
    name := "pgmq4s-anorm",
    libraryDependencies += "org.playframework.anorm" %% "anorm" % AnormV,
    mimaPreviousArtifacts := Set.empty
  )

lazy val doobie = (project in file("module/database/doobie"))
  .dependsOn(core.jvm)
  .settings(
    name := "pgmq4s-doobie",
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "doobie-core" % DoobieV,
      "org.tpolecat" %% "doobie-postgres" % DoobieV
    ),
    mimaPreviousArtifacts := Set.empty
  )

lazy val skunk = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("module/database/skunk"))
  .dependsOn(core)
  .settings(
    name := "pgmq4s-skunk",
    libraryDependencies ++= Seq(
      "org.tpolecat" %%% "skunk-core" % SkunkV,
      "org.typelevel" %%% "weaver-cats" % WeaverV % Test
    ),
    mimaPreviousArtifacts := Set.empty
  )

lazy val slick = (project in file("module/database/slick"))
  .dependsOn(core.jvm)
  .settings(
    name := "pgmq4s-slick",
    libraryDependencies += "com.typesafe.slick" %% "slick" % SlickV,
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
    libraryDependencies += "org.typelevel" %%% "weaver-cats" % WeaverV % Test,
    mimaPreviousArtifacts := Set.empty
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
    libraryDependencies += "org.typelevel" %%% "weaver-cats" % WeaverV % Test,
    mimaPreviousArtifacts := Set.empty
  )

lazy val upickle = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("module/json/upickle"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(
    name := "pgmq4s-upickle",
    libraryDependencies += "com.lihaoyi" %%% "upickle" % UpickleV,
    libraryDependencies += "org.typelevel" %%% "weaver-cats" % WeaverV % Test,
    mimaPreviousArtifacts := Set.empty
  )

lazy val playJson = (project in file("module/json/play-json"))
  .dependsOn(core.jvm % "compile->compile;test->test")
  .settings(
    name := "pgmq4s-play-json",
    libraryDependencies += "org.playframework" %% "play-json" % PlayJsonV,
    libraryDependencies += "org.typelevel" %% "weaver-cats" % WeaverV % Test,
    mimaPreviousArtifacts := Set.empty
  )

lazy val sprayJson = (project in file("module/json/spray-json"))
  .dependsOn(core.jvm % "compile->compile;test->test")
  .settings(
    name := "pgmq4s-spray-json",
    libraryDependencies += "io.spray" %% "spray-json" % SprayJsonV,
    libraryDependencies += "org.typelevel" %% "weaver-cats" % WeaverV % Test,
    mimaPreviousArtifacts := Set.empty
  )

lazy val examples = (project in file("examples"))
  .dependsOn(core.jvm, circe.jvm, anorm, doobie, skunk.jvm, slick)
  .disablePlugins(HeaderPlugin)
  .settings(
    name := "pgmq4s-examples",
    publish / skip := true,
    mimaPreviousArtifacts := Set.empty,
    coverageEnabled := false,
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "doobie-hikari" % DoobieV
    )
  )
