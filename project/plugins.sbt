addSbtPlugin("org.typelevel" % "sbt-typelevel" % "0.8.6")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.12")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "2.4.4")
// Strips fonts, Font Awesome, scaladoc-scalajs.js, and inkuire from published
// -javadoc.jars to keep the Sonatype Central footprint under the 80MB/month limit.
// Auto-applies to all modules; the full styled docs still deploy to GitHub Pages
// from the `doc` output directory (see .github/workflows/docs.yml).
addSbtPlugin("com.eed3si9n" % "sbt-salad-days" % "0.2.0")
