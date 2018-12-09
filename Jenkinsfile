#!groovy

pipeline {
  agent any
  stages {
    stage( "Parallel Stage" ) {
      parallel {
        stage( "Build / Test - JDK8" ) {
          agent { node { label 'linux' } }
          options { timeout( time: 120, unit: 'MINUTES' ) }
          steps {
            mavenBuild( "jdk8", "clean install" )
            mavenBuild( "jdk8", "deploy" )
          }
        }
        stage( "Build / Test - JDK11" ) {
          agent { node { label 'linux' } }
          options { timeout( time: 120, unit: 'MINUTES' ) }
          steps {
            mavenBuild( "jdk11", "clean install" )
          }
        }
      }
    }
  }
}

/**
 * To other developers, if you are using this method above, please use the following syntax.
 *
 * mavenBuild("<jdk>", "<profiles> <goals> <plugins> <properties>"
 *
 * @param jdk the jdk tool name (in jenkins) to use for this build
 * @param cmdline the command line in "<profiles> <goals> <properties>"`format.
 * @return the Jenkinsfile step representing a maven build
 */
def mavenBuild(jdk, cmdline) {
  def mvnName = 'maven3.5'
  def localRepo = "${env.JENKINS_HOME}/${env.EXECUTOR_NUMBER}" // ".repository" //
  def settingsName = 'oss-settings.xml'
  def mavenOpts = '-Xms2g -Xmx2g -Djava.awt.headless=true'

  withMaven(
          maven: mvnName,
          jdk: "$jdk",
          globalMavenSettingsConfig: settingsName,
          mavenOpts: mavenOpts,
          mavenLocalRepo: localRepo) {
    // Some common Maven command line + provided command line
    sh "mvn -V -B -DfailIfNoTests=false -Dmaven.test.failure.ignore=true -e $cmdline"
  }
}

// vim: et:ts=2:sw=2:ft=groovy
