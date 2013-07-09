libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.25"

addSbtPlugin("com.github.seratch" %% "scalikejdbc-mapper-generator" % "1.6.4")

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

logLevel := Level.Warn

addSbtPlugin("play" % "sbt-plugin" % "2.1.2")
