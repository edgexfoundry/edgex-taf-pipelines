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
                defaultValue: 'master', 
                description: 'GitHub PR Trigger provided parameter for specifying the commit to checkout. \
                            For downloading docker-compose file from developer-script repo'
            )
            choice(name: 'TEST_ARCH', choices: ['All', 'x86_64', 'arm64'], description: 'Test environment')
            choice(name: 'WITH_SECURITY', choices: ['All', 'No', 'Yes'], description: 'Test with security or non-security.')
        }
        environment {
            // Define test branches and device services
            BRANCHLIST = 'master'
            TAF_COMMON_IMAGE_AMD64 = 'nexus3.edgexfoundry.org:10003/docker-edgex-taf-common:latest'
            TAF_COMMON_IMAGE_ARM64 = 'nexus3.edgexfoundry.org:10003/docker-edgex-taf-common-arm64:latest'
            COMPOSE_IMAGE_AMD64 = 'nexus3.edgexfoundry.org:10003/edgex-devops/edgex-compose:latest'
            COMPOSE_IMAGE_ARM64 = 'nexus3.edgexfoundry.org:10003/edgex-devops/edgex-compose-arm64:latest'
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
                            SLAVE = edgex.getNode(config, 'amd64')
                            TAF_COMMON_IMAGE = "${TAF_COMMON_IMAGE_AMD64}"
                            COMPOSE_IMAGE = "${COMPOSE_IMAGE_AMD64}"
                        }
                        stages {
                            stage('amd64-redis'){
                                when { 
                                    expression { params.WITH_SECURITY == 'All' || params.WITH_SECURITY == 'No' }
                                }
                                environment {
                                    USE_DB = '-redis'
                                    SECURITY_SERVICE_NEEDED = false
                                }
                                steps {
                                    script {
                                        smokeTest()
                                    }
                                }
                            }
                            stage('amd64-redis-security'){
                                when { 
                                    expression { params.WITH_SECURITY == 'All' || params.WITH_SECURITY == 'Yes' }
                                }
                                environment {
                                    USE_DB = '-redis'
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
                            SLAVE = edgex.getNode(config, 'arm64')
                            TAF_COMMON_IMAGE = "${TAF_COMMON_IMAGE_ARM64}"
                            COMPOSE_IMAGE = "${COMPOSE_IMAGE_ARM64}"
                        }
                        stages {
                            stage('arm64-redis'){
                                when { 
                                    expression { params.WITH_SECURITY == 'All' || params.WITH_SECURITY == 'No' }
                                }
                                environment {
                                    USE_DB = '-redis'
                                    SECURITY_SERVICE_NEEDED = false
                                }
                                steps {
                                    script {
                                        smokeTest()
                                    }
                                }
                            }
                            stage('arm64-redis-security'){
                                when { 
                                    expression { params.WITH_SECURITY == 'All' || params.WITH_SECURITY == 'Yes' }
                                }
                                environment {
                                    USE_DB = '-redis'
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
                        def BRANCHES = "${BRANCHLIST}".split(',')
                        for (z in BRANCHES) {
                            def BRANCH = z

                            // Smoke Test Report
                            if (("${params.TEST_ARCH}" == 'All' || "${params.TEST_ARCH}" == 'x86_64')) {
                                if (("${params.WITH_SECURITY}" == 'All' || "${params.WITH_SECURITY}" == 'No')) {
                                    catchError { unstash "smoke-x86_64-redis-${BRANCH}-report" }
                                } 
                                if (("${params.WITH_SECURITY}" == 'All' || "${params.WITH_SECURITY}" == 'Yes')) {
                                    catchError { unstash "smoke-x86_64-redis-security-${BRANCH}-report" }
                                }
                            }
                            if (("${params.TEST_ARCH}" == 'All' || "${params.TEST_ARCH}" == 'arm64')) {
                                if (("${params.WITH_SECURITY}" == 'All' || "${params.WITH_SECURITY}" == 'No')) {
                                    catchError { unstash "smoke-arm64-redis-${BRANCH}-report" }
                                } 
                                if (("${params.WITH_SECURITY}" == 'All' || "${params.WITH_SECURITY}" == 'Yes')) {
                                    catchError { unstash "smoke-arm64-redis-security-${BRANCH}-report" }
                                }
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
