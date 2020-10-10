lazy val aggregatedProjects: Seq[ProjectReference] = Seq(
  `simulated-infrared-detector`,
  `simulated-infrared-detectorhcd`,
  deploy
)

lazy val `osw-prototype-components` = project
  .in(file("."))
  .aggregate(aggregatedProjects: _*)

lazy val `simulated-infrared-detector` = project
  .settings(
    libraryDependencies ++= Dependencies.SimulatedInfraredDetector
  )

lazy val `simulated-infrared-detectorhcd` = project
  .settings(
    libraryDependencies ++= Dependencies.SimulatedInfraredDetectorhcd
  )

lazy val `simple-hcd` = project
  .settings(
    libraryDependencies ++= Dependencies.SimpleHcd
  )

lazy val `simple-assembly` = project
  .settings(
    libraryDependencies ++= Dependencies.SimpleAssembly
  )

lazy val `template` = project
  .settings(
    libraryDependencies ++= Dependencies.Template
  )

lazy val deploy = project
  .dependsOn(
    `simulated-infrared-detector`,
    `simulated-infrared-detectorhcd`
  )
  .enablePlugins(JavaAppPackaging, CswBuildInfo)
  .settings(
    libraryDependencies ++= Dependencies.Deploy,
    // This is the placeholder for setting JVM options via sbt native packager.
    // You can add more JVM options below.
//    javaOptions in Universal ++= Seq(
//      // -J params will be added as jvm parameters
//      "-J-Xmx8GB",
//      "J-XX:+UseG1GC", // G1GC is default in jdk9 and above
//      "J-XX:MaxGCPauseMillis=30" // Sets a target for the maximum GC pause time. This is a soft goal, and the JVM will make its best effort to achieve it
//    )
  )
