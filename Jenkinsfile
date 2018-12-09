#!groovy

pipeline {
  agent any
  stages{
    stage ('Build') {
      agent { node { label 'linux' } }
      options { timeout(time: 120, unit: 'MINUTES') }
      steps {
        // Run test phase / ignore test failures
        withMaven( maven: 'maven3.5',
                   globalMavenSettingsConfig: 'oss-settings.xml',
                   mavenOpts: '-Xms1g -Xmx4g -Djava.awt.headless=true',
                   jdk: "jdk11",
                   mavenLocalRepo: "${env.JENKINS_HOME}/${env.EXECUTOR_NUMBER}" ) {
          sh "mvn -V -B clean install -Dmaven.test.failure.ignore=true"
        }
        //junit testResults:'**/target/surefire-reports/TEST-*.xml'
      }

    }
  }
}


// vim: et:ts=2:sw=2:ft=groovy
