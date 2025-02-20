#!groovy

pipeline {
  agent none
  // save some io during the build
  options {
    skipDefaultCheckout()
    durabilityHint('PERFORMANCE_OPTIMIZED')
    buildDiscarder logRotator( numToKeepStr: '60' )
    disableRestartFromStage()
  }
  environment {
    LAUNCHABLE_TOKEN = credentials('launchable-token')
  }
  stages {
    stage("Parallel Stage") {
      parallel {
        stage("Build / Test - JDK21") {
          agent { node { label 'linux' } }
          steps {
            timeout( time: 180, unit: 'MINUTES' ) {
              checkout scm
              mavenBuild( "jdk21", "clean install", "maven3", true)
              recordCoverage id: "coverage-jdk21", name: "Coverage jdk21", tools: [[parser: 'JACOCO']]
              mavenBuild( "jdk21", "clean javadoc:javadoc -Djacoco.skip=true", "maven3", false)
            }
          }
        }

        stage("Build / Test - JDK17") {
          agent { node { label 'linux' } }
          steps {
            timeout( time: 180, unit: 'MINUTES' ) {
              checkout scm
              mavenBuild( "jdk17", "clean install ", "maven3", true) // javadoc:javadoc
              recordCoverage id: "coverage-jdk17", name: "Coverage jdk17", tools: [[parser: 'JACOCO']]
            }
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
def mavenBuild(jdk, cmdline, mvnName, junitReport) {
  script {
    try {
      withEnv(["JAVA_HOME=${ tool "$jdk" }",
               "PATH+MAVEN=${ tool "$jdk" }/bin:${tool "$mvnName"}/bin",
               "MAVEN_OPTS=-Xms2g -Xmx4g -Djava.awt.headless=true -client -XX:+UnlockDiagnosticVMOptions -XX:GCLockerRetryAllocationCount=100"]) {
        configFileProvider(
                [configFile(fileId: 'oss-settings.xml', variable: 'GLOBAL_MVN_SETTINGS')]) {
          sh "mvn -DsettingsPath=$GLOBAL_MVN_SETTINGS -ntp -s $GLOBAL_MVN_SETTINGS -Dmaven.repo.local=.repository -V -B -e -U $cmdline"
        }
      }
    }
    finally
    {
      if(junitReport) {
        junit testResults: '**/target/surefire-reports/**/*.xml,**/target/invoker-reports/TEST*.xml', allowEmptyResults: true
      }
    }
  }
}

// vim: et:ts=2:sw=2:ft=groovy
