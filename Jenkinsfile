#!groovy

pipeline {
  agent any
  stages {
    stage("Parallel Stage") {
      parallel {
        stage("Build / Test - JDK11") {
          agent { node { label 'linux' } }
          options { timeout(time: 120, unit: 'MINUTES') }
          steps {
            mavenBuild("jdk11", "clean install")
            script {
              if (env.BRANCH_NAME == 'main') {
                mavenBuild("jdk11", "deploy")
              }
            }
          }
        }
        stage("Build / Test - JDK15") {
          agent { node { label 'linux' } }
          options { timeout(time: 120, unit: 'MINUTES') }
          steps {
            mavenBuild("jdk15", "clean install")
            // Collect up the jacoco execution results.
            jacoco inclusionPattern: '**/org/mortbay/jetty/load/generator/**/*.class',
                    execPattern: '**/target/jacoco.exec',
                    classPattern: '**/target/classes',
                    sourcePattern: '**/src/main/java'
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
  def localRepo = '${env.JENKINS_HOME}/${env.EXECUTOR_NUMBER}'
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
