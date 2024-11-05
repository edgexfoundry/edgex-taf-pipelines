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

        parameters {
            choice(name: 'TEST_ARCH', choices: ['All', 'x86_64', 'arm64'])
            choice(name: 'WITH_SECURITY', choices: ['All', 'No', 'Yes'])
            string(name: 'TAF_BRANCH', defaultValue: 'heads/main', description: 'Test branch for edgexfoundry/edgex-taf repository. Examples: tags/tag or heads/branch')
            string(name: 'COMPOSE_BRANCH', defaultValue: 'main', description: 'Test branch for edgexfoundry/edgex-compose repository. Examples: master or ireland')
        }

        environment {
            // Define test branches and device services
            PROFILELIST = 'device-virtual,device-modbus'
            TAF_COMMON_IMAGE_AMD64 = 'nexus3.edgexfoundry.org:10003/edgex-taf-common:latest'
            TAF_COMMON_IMAGE_ARM64 = 'nexus3.edgexfoundry.org:10003/edgex-taf-common-arm64:latest'
            COMPOSE_IMAGE = 'docker:26.0.1'
            TAF_BRANCH = "${params.TAF_BRANCH}"
            COMPOSE_BRANCH = "${params.COMPOSE_BRANCH}"
        }

        stages {
            stage ('Run Test') {
                parallel {
                    stage ('Run Test on amd64') {
                        when { 
                            expression { params.TEST_ARCH == 'All' || params.TEST_ARCH == 'x86_64' }
                        }
                        environment {
                            ARCH = 'x86_64'
                            NODE = edgex.getNode(config, 'amd64')
                            TAF_COMMON_IMAGE = "${TAF_COMMON_IMAGE_AMD64}"
                        }
                        stages {
                            stage('amd64'){
                                when { 
                                    expression { params.WITH_SECURITY == 'All' || params.WITH_SECURITY == 'No' }
                                }
                                environment {
                                    SECURITY_SERVICE_NEEDED = false
                                }
                                steps {
                                    script {
                                        startTest()
                                    }
                                }
                            }
                            stage('amd64-security'){
                                when { 
                                    expression { params.WITH_SECURITY == 'All' || params.WITH_SECURITY == 'Yes' }
                                }
                                environment {
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
                        when { 
                            expression { params.TEST_ARCH == 'All' || params.TEST_ARCH == 'arm64' }
                        }
                        environment {
                            ARCH = 'arm64'
                            NODE = edgex.getNode(config, 'arm64')
                            TAF_COMMON_IMAGE = "${TAF_COMMON_IMAGE_ARM64}"
                        }
                        stages {
                            stage('arm64'){
                                when { 
                                    expression { params.WITH_SECURITY == 'All' || params.WITH_SECURITY == 'No' }
                                }
                                environment {
                                    SECURITY_SERVICE_NEEDED = false
                                }
                                steps {
                                    script {
                                        startTest()
                                    }
                                }
                            }
                            stage('arm64-security'){
                                when { 
                                    expression { params.WITH_SECURITY == 'All' || params.WITH_SECURITY == 'Yes' }
                                }
                                environment {
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
                        if (("${params.TEST_ARCH}" == 'All' || "${params.TEST_ARCH}" == 'x86_64')) {
                            if (("${params.WITH_SECURITY}" == 'All' || "${params.WITH_SECURITY}" == 'No')) {
                                catchError { unstash "x86_64-report" }
                            }
                            if (("${params.WITH_SECURITY}" == 'All' || "${params.WITH_SECURITY}" == 'Yes')) {
                                catchError { unstash "x86_64-security-report" }
                            }
                        }
                        if (("${params.TEST_ARCH}" == 'All' || "${params.TEST_ARCH}" == 'arm64')) {
                            if (("${params.WITH_SECURITY}" == 'All' || "${params.WITH_SECURITY}" == 'No')) {
                                catchError { unstash "arm64-report" }
                            }
                            if (("${params.WITH_SECURITY}" == 'All' || "${params.WITH_SECURITY}" == 'Yes')) {
                                catchError { unstash "arm64-security-report" }
                            }
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
                            keepAll: true,
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
        timeout(time: 80, unit: 'MINUTES') {
            def rootDir = pwd()
            def runTestScripts = load "${rootDir}/runTestScripts.groovy"
            runTestScripts.main()
        }
    }
}
