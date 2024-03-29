#!groovy

def oss = ["linux"]
def jdks = ["jdk11", "jdk17", "jdk21"]

def builds = [:]
for (def os in oss) {
  for (def jdk in jdks) {
    builds[os + "_" + jdk] = newBuild(os, jdk)
  }
}

parallel builds

def newBuild(os, jdk) {
  return {
    node(os) {
      def mvnName = 'maven3'
      def settingsName = 'oss-settings.xml'
      def mvnOpts = '-Xms2g -Xmx4g -Djava.awt.headless=true'

      stage("Checkout - ${jdk}") {
        checkout scm
      }

      stage("Build - ${jdk}") {
        timeout(time: 1, unit: 'HOURS') {
          withMaven(maven: mvnName,
                  jdk: jdk,
                  publisherStrategy: 'EXPLICIT',
                  globalMavenSettingsConfig: settingsName,
                  mavenOpts: mvnOpts) {
            sh "mvn -V -B clean install -Dmaven.test.failure.ignore=true -e"
          }

          junit testResults: '**/target/surefire-reports/TEST-*.xml'
          // Collect the JaCoCo execution results.
          jacoco inclusionPattern: '**/org/mortbay/jetty/load/generator/**/*.class',
                  execPattern: '**/target/jacoco.exec',
                  classPattern: '**/target/classes',
                  sourcePattern: '**/src/main/java'
        }
      }

      stage("Javadoc - ${jdk}") {
        timeout(time: 15, unit: 'MINUTES') {
          withMaven(maven: mvnName,
                  jdk: jdk,
                  publisherStrategy: 'EXPLICIT',
                  globalMavenSettingsConfig: settingsName,
                  mavenOpts: mvnOpts) {
            sh "mvn -V -B javadoc:javadoc -e"
          }
        }
      }
    }
  }
}
