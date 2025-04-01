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
            choice(name: 'TEST_STRATEGY', choices: ['IntegrationTest'])
            choice(name: 'TEST_ARCH', choices: ['All', 'x86_64', 'arm64'])
            choice(name: 'WITH_SECURITY', choices: ['All', 'No', 'Yes'])
            choice(name: 'TEST_BUS', choices: ['All', 'MQTT', 'None'], description: 'None for only run Delayed Start Case')
            string(name: 'TAF_BRANCH', defaultValue: 'heads/main', description: 'Test branch for edgexfoundry/edgex-taf repository. Examples: tags/tag or heads/branch')
            string(name: 'COMPOSE_BRANCH', defaultValue: 'main', description: 'Test branch for edgexfoundry/edgex-compose repository. Examples: main or ireland')
        }
        environment {
            // Define test branches and device services
            TAF_COMMON_IMAGE = 'nexus3.edgexfoundry.org:10003/edgex-taf-common:latest'
            COMPOSE_IMAGE = 'docker:28.0.1'
            TAF_BRANCH = "${params.TAF_BRANCH}"
            COMPOSE_BRANCH = "${params.COMPOSE_BRANCH}"
            TEST_BUS = "${params.TEST_BUS}"
        }
        stages {
            stage ('Run Test') {
                parallel {
                    stage ('Run Integration Test on amd64') {
                        when { 
                            expression { params.TEST_ARCH == 'All' || params.TEST_ARCH == 'x86_64' }
                        }
                        environment {
                            ARCH = 'x86_64'
                            NODE = edgex.getNode(config, 'amd64')
                        }
                        stages {
                            stage('amd64'){
                                when { 
                                    expression { params.WITH_SECURITY == 'All' || params.WITH_SECURITY == 'No' }
                                    expression { params.TEST_BUS != 'None' }
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
                    stage ('Run Integration Test on arm64') {
                        when { 
                            expression { params.TEST_ARCH == 'All' || params.TEST_ARCH == 'arm64' }
                        }
                        environment {
                            ARCH = 'arm64'
                            NODE = edgex.getNode(config, 'arm64')
                        }
                        stages {
                            stage('arm64'){
                                when { 
                                    expression { params.WITH_SECURITY == 'All' || params.WITH_SECURITY == 'No' }
                                    expression { params.TEST_BUS != 'None' }
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
                }
            }
            stage ('Publish Robotframework Report...') {
                steps{
                    script {
                        // Integration Test Report
                        if (("${params.TEST_ARCH}" == 'All' || "${params.TEST_ARCH}" == 'x86_64')) {
                            if (("${params.WITH_SECURITY}" == 'All' || "${params.WITH_SECURITY}" == 'No')) {
                                if (("${params.TEST_BUS}" != 'None')) {
                                    catchError { unstash "integration-x86_64-report" }
                                }
                            }
                            if (("${params.WITH_SECURITY}" == 'All' || "${params.WITH_SECURITY}" == 'Yes')) {
                                catchError { unstash "integration-x86_64-security-report" }
                            }
                        }
                        if (("${params.TEST_ARCH}" == 'All' || "${params.TEST_ARCH}" == 'arm64')) {
                            if (("${params.WITH_SECURITY}" == 'All' || "${params.WITH_SECURITY}" == 'No')) {
                                if (("${params.TEST_BUS}" != 'None')) {
                                    catchError { unstash "integration-arm64-report" }
                                }
                            }
                            if (("${params.WITH_SECURITY}" == 'All' || "${params.WITH_SECURITY}" == 'Yes')) {
                                catchError { unstash "integration-arm64-security-report" }
                            }
                        }

                        dir ('TAF/testArtifacts/reports/merged-report/') {
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
                    }

                    junit 'TAF/testArtifacts/reports/merged-report/**.xml'
                }
            }
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
