#!/usr/bin/env groovy
def LOGFILES

call([
    project: 'smoke-test',
])

def call(config) {
    edgex.bannerMessage "[smoke-testing] RAW Config: ${config}"

    edgeXGeneric.validate(config)
    edgex.setupNodes(config)

    def _envVarMap = edgeXGeneric.toEnvironment(config)

    pipeline {
        agent { label edgex.mainNode(config) }
        options {
            timestamps()
        }
        parameters {
            string(
                name: 'SHA1',
                defaultValue: 'main',
                description: 'GitHub PR Trigger provided parameter for specifying the commit to checkout. \
                            For downloading docker-compose file from developer-script repo'
            )
            choice(name: 'TEST_ARCH', choices: ['All', 'x86_64', 'arm64'], description: 'Test environment')
            choice(name: 'WITH_SECURITY', choices: ['All', 'No', 'Yes'], description: 'Test with security or non-security.')
            choice(name: 'REGISTRY_SERVICE', choices: ['Keeper', 'Consul'])
        }
        environment {
            // Define test branches and device services
            TAF_BRANCH = 'main'
            TAF_COMMON_IMAGE_AMD64 = 'nexus3.edgexfoundry.org:10003/edgex-taf-common:latest'
            TAF_COMMON_IMAGE_ARM64 = 'nexus3.edgexfoundry.org:10003/edgex-taf-common-arm64:latest'
            COMPOSE_IMAGE = 'docker:26.0.1'
            REGISTRY_SERVICE = "${params.REGISTRY_SERVICE}"
        }
        stages { 
            stage ('Run Test') {
                parallel {
                    stage ('Run Smoke Test on amd64') {
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
                                        smokeTest()
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
                                        smokeTest()
                                    }
                                }
                            }
                        }
                    }
                    stage ('Run Smoke Test on arm64') {
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
                                        smokeTest()
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
                                        smokeTest()
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
                        // Smoke Test Report
                        if (("${params.TEST_ARCH}" == 'All' || "${params.TEST_ARCH}" == 'x86_64')) {
                            if (("${params.WITH_SECURITY}" == 'All' || "${params.WITH_SECURITY}" == 'No')) {
                                catchError { unstash "smoke-x86_64-${TAF_BRANCH}-report" }
                            }
                            if (("${params.WITH_SECURITY}" == 'All' || "${params.WITH_SECURITY}" == 'Yes')) {
                                catchError { unstash "smoke-x86_64-security-${TAF_BRANCH}-report" }
                            }
                        }
                        if (("${params.TEST_ARCH}" == 'All' || "${params.TEST_ARCH}" == 'arm64')) {
                            if (("${params.WITH_SECURITY}" == 'All' || "${params.WITH_SECURITY}" == 'No')) {
                                catchError { unstash "smoke-arm64-${TAF_BRANCH}-report" }
                            }
                            if (("${params.WITH_SECURITY}" == 'All' || "${params.WITH_SECURITY}" == 'Yes')) {
                                catchError { unstash "smoke-arm64-security-${TAF_BRANCH}-report" }
                            }
                        }

                        dir ('TAF/testArtifacts/reports/merged-report/') {
                            SMOKE_LOGFILES= sh (
                                script: 'ls smoke-*-log.html | sed ":a;N;s/\\n/,/g;ta"',
                                returnStdout: true
                            )
                            publishHTML(
                                target: [
                                    allowMissing: false,
                                    alwaysLinkToLastBuild: false,
                                    keepAll: true,
                                    reportDir: '.',
                                    reportFiles: "${SMOKE_LOGFILES}",
                                    reportName: 'Smoke Test Reports']
                            )
                        }
                    }
                    
                    junit 'TAF/testArtifacts/reports/merged-report/**.xml'
                }                                         
            }
        }
    }
} 


def smokeTest() {
    catchError {
        timeout(time: 30, unit: 'MINUTES') {
            def rootDir = pwd()
            def runSmokeTestScripts = load "${rootDir}/runSmokeTestScripts.groovy"
            runSmokeTestScripts.main()
        }
    }      
}
