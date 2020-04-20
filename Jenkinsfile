#!/usr/bin/env groovy
def LOGFILES

call([
    project: 'integration-test',
    mavenSettings: ['integration-testing-settings:SETTINGS_FILE']
])

def call(config) {
    edgex.bannerMessage "[integration-testing] RAW Config: ${config}"

    edgeXGeneric.validate(config)
    edgex.setupNodes(config)

    def _envVarMap = edgeXGeneric.toEnvironment(config)

    pipeline {
        agent { label edgex.mainNode(config) }
        triggers { cron('H 7 * * *') }
        options {
            timestamps()
        }
        environment {
            // Define test branches and device services
            BRANCHLIST = 'master'
            PROFILELIST = 'device-virtual'
            TAF_COMMOM_IMAGE = 'nexus3.edgexfoundry.org:10003/docker-edgex-taf-common:latest'
            COMPOSE_IMAGE = 'nexus3.edgexfoundry.org:10003/edgex-devops/edgex-compose:latest'
            ARCH = 'x86_64'
            SLAVE = edgex.getNode(config, 'amd64')
            SECURITY_SERVICE_NEEDED = false
        }
        stages {
            stage ('Run Test') {
                parallel {
                    stage('amd64-redis'){
                        environment {
                            USE_DB = '-redis'
                        }
                        steps {
                            script {
                                backwardTest()
                            }
                        }
                    }
                    stage('amd64-mongo'){
                        environment {
                            USE_DB = '-mongo'
                        }
                        steps {
                            script {
                                backwardTest()
                            }
                        }
                    }
                }
            }
            stage ('Publish Robotframework Report...') {
                steps{
                    script {
                        def BRANCHES = "${BRANCHLIST}".split(',')
                        for (z in BRANCHES) {
                            def BRANCH = z

                            catchError { unstash "backward-x86_64-redis-${BRANCH}-fuji-report" }
                            catchError { unstash "backward-x86_64-mongo-${BRANCH}-fuji-report" }
                        }
                        dir ('TAF/testArtifacts/reports/merged-report/') {
                            LOGFILES= sh (
                                script: 'ls *-log.html | sed ":a;N;s/\\n/,/g;ta"',
                                returnStdout: true
                            )
                        }
                    }
                    publishHTML(
                        target: [
                            allowMissing: false,
                            alwaysLinkToLastBuild: true,
                            keepAll: false,
                            reportDir: 'TAF/testArtifacts/reports/merged-report',
                            reportFiles: "${LOGFILES}",
                            reportName: 'integration Test Reports']
                    )

                    junit 'TAF/testArtifacts/reports/merged-report/**.xml'
                }                                         
            }
        }
    }
} 

def backwardTest() {
    catchError {
        timeout(time: 30, unit: 'MINUTES') {
            def rootDir = pwd()
            def runBackwardTestScripts = load "${rootDir}/runBackwardTestScripts.groovy"
            runBackwardTestScripts.main()
        }
    }      
}
