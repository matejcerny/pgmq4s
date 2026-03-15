ThisBuild / tlBaseVersion := "0.1"
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
val CatsEffectV = "3.6.3"
val CirceV = "0.14.8"
val DoobieV = "1.0.0-RC12"
val SkunkV = "0.6.5"
val ScalaJavaTimeV = "2.6.0"
val JsoniterV = "2.30.2"
val PostgresV = "42.7.5"
val SlickV = "3.6.1"
val WeaverV = "0.11.3"

lazy val root = tlCrossRootProject
  .settings(name := "pgmq4s")
  .aggregate(core, circe, jsoniter, doobie, skunk, slick, examples)

lazy val integration = crossProject(JVMPlatform, NativePlatform)
  .crossType(CrossType.Full)
  .in(file("it"))
  .dependsOn(skunk, circe)
  .jvmConfigure(_.dependsOn(doobie, slick))
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

lazy val skunk = crossProject(JVMPlatform, JSPlatform, NativePlatform)
  .crossType(CrossType.Pure)
  .in(file("module/database/skunk"))
  .dependsOn(core)
  .settings(
    name := "pgmq4s-skunk",
    libraryDependencies ++= Seq(
      "org.tpolecat" %%% "skunk-core" % SkunkV,
      "org.typelevel" %%% "weaver-cats" % WeaverV % Test
    )
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

// === DOCUMENTATION ===
lazy val docs = project
  .in(file("site"))
  .dependsOn(core.jvm, circe.jvm, jsoniter.jvm, doobie, skunk.jvm, slick)
  .enablePlugins(TypelevelSitePlugin)
  .settings(tlSitePublishBranch := Some("main"))

lazy val examples = (project in file("examples"))
  .dependsOn(core.jvm, circe.jvm, doobie, skunk.jvm, slick)
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
