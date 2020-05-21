#!/usr/bin/env groovy
def LOGFILES

call([
    project: 'funcational-test'
])

def call(config) {
    edgex.bannerMessage "[functional-testing] RAW Config: ${config}"

    edgeXGeneric.validate(config)
    edgex.setupNodes(config)

    def _envVarMap = edgeXGeneric.toEnvironment(config)

    pipeline {
        agent { label edgex.mainNode(config) }
        triggers { cron('H 0 * * *') }
        options { 
            timestamps()
        }

        environment {
            // Define test branches and device services
            BRANCHLIST = 'master' // Branch in edgex-taf repo
            PROFILELIST = 'device-virtual,device-modbus'
            TAF_COMMOM_IMAGE_AMD64 = 'nexus3.edgexfoundry.org:10003/docker-edgex-taf-common:latest'
            TAF_COMMOM_IMAGE_ARM64 = 'nexus3.edgexfoundry.org:10003/docker-edgex-taf-common-arm64:latest'
            COMPOSE_IMAGE_AMD64 = 'nexus3.edgexfoundry.org:10003/edgex-devops/edgex-compose:latest'
            COMPOSE_IMAGE_ARM64 = 'nexus3.edgexfoundry.org:10003/edgex-devops/edgex-compose-arm64:latest'
        }

        stages {
            stage ('Run Test') {
                parallel {
                    stage ('Run Test on amd64') {
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
                                        startTest()
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
                                        startTest()
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
                                        startTest()
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
                                        startTest()
                                    }
                                }
                            }
                        }
                    }

                    stage ('Run Test on arm64') {
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
                                        startTest()
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
                                        startTest()
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
                                        startTest()
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
                                        startTest()
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

                            catchError { unstash "x86_64-redis-${BRANCH}-report" }
                            catchError { unstash "x86_64-redis-security-${BRANCH}-report" }
                            catchError { unstash "x86_64-mongo-${BRANCH}-report" }
                            catchError { unstash "x86_64-mongo-security-${BRANCH}-report" }
                            catchError { unstash "arm64-redis-${BRANCH}-report" }
                            catchError { unstash "arm64-redis-security-${BRANCH}-report" }
                            catchError { unstash "arm64-mongo-${BRANCH}-report" }
                            catchError { unstash "arm64-mongo-security-${BRANCH}-report" }
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
                            reportName: 'Functional Test Reports']
                    )

                    junit 'TAF/testArtifacts/reports/merged-report/**.xml'
                }
            }
        }
    }
}

def startTest() {
    catchError {
        timeout(time: 1, unit: 'HOURS') {
            def rootDir = pwd()
            def runTestScripts = load "${rootDir}/runTestScripts.groovy"
            runTestScripts.main()
        }
    }
}
