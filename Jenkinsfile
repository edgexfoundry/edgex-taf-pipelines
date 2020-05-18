#!/usr/bin/env groovy
def LOGFILES

call([
    project: 'integration-test',
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
            TAF_COMMOM_IMAGE_AMD64 = 'nexus3.edgexfoundry.org:10003/docker-edgex-taf-common:latest'
            TAF_COMMOM_IMAGE_ARM64 = 'nexus3.edgexfoundry.org:10003/docker-edgex-taf-common-arm64:latest'
            COMPOSE_IMAGE_AMD64 = 'nexus3.edgexfoundry.org:10003/edgex-devops/edgex-compose:latest'
            COMPOSE_IMAGE_ARM64 = 'nexus3.edgexfoundry.org:10003/edgex-devops/edgex-compose-arm64:latest'
        }
        stages {
            stage ('Run Test') {
                parallel {
                    stage ('Run Integration Test on amd64') {
                        environment {
                            ARCH = 'x86_64'
                            SLAVE = edgex.getNode(config, 'amd64')
                            TAF_COMMOM_IMAGE = "${TAF_COMMOM_IMAGE_AMD64}"
                            COMPOSE_IMAGE = "${COMPOSE_IMAGE_AMD64}"
                        }
                        stages {
                            stage('amd64-redis'){
                                environment {
                                    USE_DB = '-redis'
                                    SECURITY_SERVICE_NEEDED = false
                                }
                                steps {
                                    script {
                                        integrationTest()
                                    }
                                }
                            }
                            stage('amd64-redis-security'){
                                environment {
                                    USE_DB = '-redis'
                                    SECURITY_SERVICE_NEEDED = true
                                }
                                steps {
                                    script {
                                        integrationTest()
                                    }
                                }
                            }
                            stage('amd64-mongo'){
                                environment {
                                    USE_DB = '-mongo'
                                    SECURITY_SERVICE_NEEDED = false
                                }
                                steps {
                                    script {
                                        integrationTest()
                                    }
                                }
                            }
                            stage('amd64-mongo-security'){
                                environment {
                                    USE_DB = '-mongo'
                                    SECURITY_SERVICE_NEEDED = true
                                }
                                steps {
                                    script {
                                        integrationTest()
                                    }
                                }
                            }
                        }
                    }
                    // stage ('Run Backward Test on amd64') {
                    //     environment {
                    //         ARCH = 'x86_64'
                    //         SLAVE = edgex.getNode(config, 'amd64')
                    //         TAF_COMMOM_IMAGE = "${TAF_COMMOM_IMAGE_AMD64}"
                    //         COMPOSE_IMAGE = "${COMPOSE_IMAGE_AMD64}"
                    //         SECURITY_SERVICE_NEEDED = false
                    //     }
                    //     stages {
                    //         stage('backward-amd64-redis'){
                    //             environment {
                    //                 USE_DB = '-redis'
                    //             }
                    //             steps {
                    //                 script {
                    //                     backwardTest()
                    //                 }
                    //             }
                    //         }
                    //         stage('backward-amd64-mongo'){
                    //             environment {
                    //                 USE_DB = '-mongo'
                    //             }
                    //             steps {
                    //                 script {
                    //                     backwardTest()
                    //                 }
                    //             }
                    //         }
                    //     }
                    // }
                    stage ('Run Integration Test on arm64') {
                        environment {
                            ARCH = 'arm64'
                            SLAVE = edgex.getNode(config, 'arm64')
                            TAF_COMMOM_IMAGE = "${TAF_COMMOM_IMAGE_ARM64}"
                            COMPOSE_IMAGE = "${COMPOSE_IMAGE_ARM64}"
                        }
                        stages {
                            stage('arm64-redis'){
                                environment {
                                    USE_DB = '-redis'
                                    SECURITY_SERVICE_NEEDED = false
                                }
                                steps {
                                    script {
                                        integrationTest()
                                    }
                                }
                            }
                            stage('arm64-redis-security'){
                                environment {
                                    USE_DB = '-redis'
                                    SECURITY_SERVICE_NEEDED = true
                                }
                                steps {
                                    script {
                                        integrationTest()
                                    }
                                }
                            }
                            stage('arm64-mongo'){
                                environment {
                                    USE_DB = '-mongo'
                                    SECURITY_SERVICE_NEEDED = false
                                }
                                steps {
                                    script {
                                        integrationTest()
                                    }
                                }
                            }
                            stage('arm64-mongo-security'){
                                environment {
                                    USE_DB = '-mongo'
                                    SECURITY_SERVICE_NEEDED = true
                                }
                                steps {
                                    script {
                                        integrationTest()
                                    }
                                }
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
                            // Backward Test Report
                            // catchError { unstash "backward-x86_64-redis-${BRANCH}-fuji-report" }
                            // catchError { unstash "backward-x86_64-mongo-${BRANCH}-fuji-report" }

                            // Integration Test Report
                            catchError { unstash "integration-x86_64-redis-${BRANCH}-report" }
                            catchError { unstash "integration-x86_64-redis-security-${BRANCH}-report" }
                            catchError { unstash "integration-x86_64-mongo-${BRANCH}-report" }
                            catchError { unstash "integration-x86_64-mongo-security-${BRANCH}-report" }
                            catchError { unstash "integration-arm64-redis-${BRANCH}-report" }
                            catchError { unstash "integration-arm64-redis-security-${BRANCH}-report" }
                            catchError { unstash "integration-arm64-mongo-${BRANCH}-report" }
                            catchError { unstash "integration-arm64-mongo-security-${BRANCH}-report" }
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
                            reportName: 'Integration Test Reports']
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

def integrationTest() {
    catchError {
        timeout(time: 30, unit: 'MINUTES') {
            def rootDir = pwd()
            def runIntegrationTestScripts = load "${rootDir}/runIntegrationTestScripts.groovy"
            runIntegrationTestScripts.main()
        }
    }      
}
