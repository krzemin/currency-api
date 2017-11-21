val akkaHttpVersion = "10.0.10"
val akkaVersion    = "2.5.7"
val circeVersion = "0.8.0"

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization    := "com.example",
      scalaVersion    := "2.12.4"
    )),
    name := "currency-api",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion,
      "de.heikoseeberger" %% "akka-http-circe"      % "1.18.1",
      "com.typesafe.akka" %% "akka-stream"          % akkaVersion,
      "io.circe"          %% "circe-core"           % circeVersion,
      "io.circe"          %% "circe-generic"        % circeVersion,
      "io.circe"          %% "circe-parser"         % circeVersion,
      "io.circe"          %% "circe-java8"          % circeVersion,

      "com.typesafe.akka" %% "akka-http-testkit"    % akkaHttpVersion % Test,
      "com.typesafe.akka" %% "akka-testkit"         % akkaVersion     % Test,
      "com.typesafe.akka" %% "akka-stream-testkit"  % akkaVersion     % Test,
      "org.scalatest"     %% "scalatest"            % "3.0.4"         % Test
    )
  )
