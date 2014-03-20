import sbt._, Keys._
import play.Project._

object DevTeamBuild extends Build {

  lazy val scalikejdbcVersion = "1.6.4"
  lazy val h2Version = "1.3.172"

  lazy val app = {
    val appName         = "hello-scalikejdbc"
    val appVersion      = "0.1"
    val appDependencies = Seq(
      "com.github.seratch"   %% "scalikejdbc"                     % scalikejdbcVersion,
      "com.github.seratch"   %% "scalikejdbc-config"              % scalikejdbcVersion,
      "com.github.seratch"   %% "scalikejdbc-interpolation"       % scalikejdbcVersion,
      "com.github.seratch"   %% "scalikejdbc-play-plugin"         % scalikejdbcVersion,
      "com.github.seratch"   %% "scalikejdbc-play-fixture-plugin" % scalikejdbcVersion,
      "mysql"                %  "mysql-connector-java"            % "5.1.25",
      "org.hibernate"        %  "hibernate-core"                  % "4.2.2.Final",
      "org.json4s"           %% "json4s-ext"                      % "3.2.4",
      "com.github.tototoshi" %% "play-json4s-native"              % "0.1.0",
      "com.github.tototoshi" %% "play-flyway"                     % "0.1.3",
      "com.github.seratch"   %% "scalikejdbc-test"                % scalikejdbcVersion  % "test",
      "org.specs2"           %% "specs2"                          % "2.0"               % "test"
    )
    play.Project(appName, appVersion, appDependencies).settings(scalaVersion in ThisBuild := "2.10.2")
  }

}
