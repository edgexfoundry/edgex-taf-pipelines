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
        triggers { cron('H 1 * * *') }
        options {
            timestamps()
        }
        parameters {
            choice(name: 'TEST_STRATEGY', choices: ['IntegrationTest', 'BackwardTest', 'All'])
            choice(name: 'TEST_ARCH', choices: ['All', 'x86_64', 'arm64'])
            choice(name: 'WITH_SECURITY', choices: ['All', 'No', 'Yes'])
            string(name: 'TAF_BRANCH', defaultValue: 'heads/main', description: 'Test branch for edgexfoundry/edgex-taf repository. Examples: tags/tag or heads/branch')
            string(name: 'COMPOSE_BRANCH', defaultValue: 'main', description: 'Test branch for edgexfoundry/edgex-compose repository. Examples: main or ireland')
        }
        environment {
            // Define test branches and device services
            TAF_COMMON_IMAGE_AMD64 = 'nexus3.edgexfoundry.org:10003/edgex-taf-common:latest'
            TAF_COMMON_IMAGE_ARM64 = 'nexus3.edgexfoundry.org:10003/edgex-taf-common-arm64:latest'
            COMPOSE_IMAGE_AMD64 = 'nexus3.edgexfoundry.org:10003/edgex-devops/edgex-compose:latest'
            COMPOSE_IMAGE_ARM64 = 'nexus3.edgexfoundry.org:10003/edgex-devops/edgex-compose-arm64:latest'
            TAF_BRANCH = "${params.TAF_BRANCH}"
            COMPOSE_BRANCH = "${params.COMPOSE_BRANCH}"

            // Use on backword compatibility test
            BCT_RELEASE = 'geneva'
        }
        stages {
            stage ('Run Test') {
                parallel {
                    stage ('Run Integration Test on amd64') {
                        when { 
                            expression { params.TEST_STRATEGY == 'All' || params.TEST_STRATEGY == 'IntegrationTest' }
                            expression { params.TEST_ARCH == 'All' || params.TEST_ARCH == 'x86_64' }
                        }
                        environment {
                            ARCH = 'x86_64'
                            NODE = edgex.getNode(config, 'amd64')
                            TAF_COMMON_IMAGE = "${TAF_COMMON_IMAGE_AMD64}"
                            COMPOSE_IMAGE = "${COMPOSE_IMAGE_AMD64}"
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
                                        integrationTest()
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
                                        integrationTest()
                                    }
                                }
                            }
                        }
                    }
                    stage ('Run Backward Test on amd64') {
                        when { 
                            expression { params.TEST_STRATEGY == 'All' || params.TEST_STRATEGY == 'BackwardTest' }
                            expression { params.TEST_ARCH == 'All' || params.TEST_ARCH == 'x86_64' }
                        }
                        environment {
                            ARCH = 'x86_64'
                            NODE = edgex.getNode(config, 'amd64')
                            TAF_COMMON_IMAGE = "${TAF_COMMON_IMAGE_AMD64}"
                            COMPOSE_IMAGE = "${COMPOSE_IMAGE_AMD64}"
                        }
                        stages {
                            stage('backward-amd64'){
                                when { 
                                    expression { params.WITH_SECURITY == 'All' || params.WITH_SECURITY == 'No' }
                                }
                                environment {
                                    SECURITY_SERVICE_NEEDED = false
                                }
                                steps {
                                    script {
                                        backwardTest()
                                    }
                                }
                            }
                        }
                    }
                    stage ('Run Integration Test on arm64') {
                        when { 
                            expression { params.TEST_STRATEGY == 'All' || params.TEST_STRATEGY == 'IntegrationTest' }
                            expression { params.TEST_ARCH == 'All' || params.TEST_ARCH == 'arm64' }
                        }
                        environment {
                            ARCH = 'arm64'
                            NODE = edgex.getNode(config, 'arm64')
                            TAF_COMMON_IMAGE = "${TAF_COMMON_IMAGE_ARM64}"
                            COMPOSE_IMAGE = "${COMPOSE_IMAGE_ARM64}"
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
                                        integrationTest()
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
                                        integrationTest()
                                    }
                                }
                            }
                        }
                    }
                    stage ('Run Backward Test on arm64') {
                        when { 
                            expression { params.TEST_STRATEGY == 'All' || params.TEST_STRATEGY == 'BackwardTest' }
                            expression { params.TEST_ARCH == 'All' || params.TEST_ARCH == 'arm64' }
                        }
                        environment {
                            ARCH = 'arm64'
                            NODE = edgex.getNode(config, 'arm64')
                            TAF_COMMON_IMAGE = "${TAF_COMMON_IMAGE_ARM64}"
                            COMPOSE_IMAGE = "${COMPOSE_IMAGE_ARM64}"
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
                                        backwardTest()
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
                        // Backward Test Report
                        if (("${params.TEST_STRATEGY}" == 'All' || "${params.TEST_STRATEGY}" == 'BackwardTest')) {
                            if (("${params.TEST_ARCH}" == 'All' || "${params.TEST_ARCH}" == 'x86_64')) {
                                if (("${params.WITH_SECURITY}" == 'All' || "${params.WITH_SECURITY}" == 'No')) {
                                    catchError { unstash "backward-x86_64-${BCT_RELEASE}-report" }
                                }
                            }
                            if (("${params.TEST_ARCH}" == 'All' || "${params.TEST_ARCH}" == 'arm64')) {
                                if (("${params.WITH_SECURITY}" == 'All' || "${params.WITH_SECURITY}" == 'No')) {
                                    catchError { unstash "backward-arm64-${BCT_RELEASE}-report" }
                                }
                            }
                        }

                        // Integration Test Report
                        if (("${params.TEST_STRATEGY}" == 'All' || "${params.TEST_STRATEGY}" == 'IntegrationTest')) {
                            if (("${params.TEST_ARCH}" == 'All' || "${params.TEST_ARCH}" == 'x86_64')) {
                                if (("${params.WITH_SECURITY}" == 'All' || "${params.WITH_SECURITY}" == 'No')) {
                                    catchError { unstash "integration-x86_64-report" }
                                }
                                if (("${params.WITH_SECURITY}" == 'All' || "${params.WITH_SECURITY}" == 'Yes')) {
                                    catchError { unstash "integration-x86_64-security-report" }
                                }
                            }
                            if (("${params.TEST_ARCH}" == 'All' || "${params.TEST_ARCH}" == 'arm64')) {
                                if (("${params.WITH_SECURITY}" == 'All' || "${params.WITH_SECURITY}" == 'No')) {
                                    catchError { unstash "integration-arm64-report" }
                                }
                                if (("${params.WITH_SECURITY}" == 'All' || "${params.WITH_SECURITY}" == 'Yes')) {
                                    catchError { unstash "integration-arm64-security-report" }
                                }
                            }
                        }

                        dir ('TAF/testArtifacts/reports/merged-report/') {
                            if (("${params.TEST_STRATEGY}" == 'All' || "${params.TEST_STRATEGY}" == 'IntegrationTest')) {
                                INTEGRATION_LOGFILES= sh (
                                    script: 'ls integration-*-log.html | sed ":a;N;s/\\n/,/g;ta"',
                                    returnStdout: true
                                )
                                publishHTML(
                                    target: [
                                        allowMissing: false,
                                        alwaysLinkToLastBuild: false,
                                        keepAll: true,
                                        reportDir: '.',
                                        reportFiles: "${INTEGRATION_LOGFILES}",
                                        reportName: 'Integration Test Reports']
                                )
                            }

                            if (("${params.TEST_STRATEGY}" == 'All' || "${params.TEST_STRATEGY}" == 'BackwardTest')) {
                                BACKWARD_LOGFILES= sh (
                                    script: 'ls backward-*-log.html | sed ":a;N;s/\\n/,/g;ta"',
                                    returnStdout: true
                                )
                                publishHTML(
                                    target: [
                                        allowMissing: false,
                                        alwaysLinkToLastBuild: false,
                                        keepAll: true,
                                        reportDir: '.',
                                        reportFiles: "${BACKWARD_LOGFILES}",
                                        reportName: 'Backward Test Reports']
                                )
                            }
                        }
                    }

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
        timeout(time: 50, unit: 'MINUTES') {
            def rootDir = pwd()
            def runIntegrationTestScripts = load "${rootDir}/runIntegrationTestScripts.groovy"
            runIntegrationTestScripts.main()
        }
    }      
}
