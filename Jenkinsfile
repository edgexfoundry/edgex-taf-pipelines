#!/usr/bin/env groovy
def LOGFILES

call([
    project: 'performance-test',
])

def call(config) {
    edgex.bannerMessage "[performance-testing] RAW Config: ${config}"

    edgeXGeneric.validate(config)
    edgex.setupNodes(config)

    def _envVarMap = edgeXGeneric.toEnvironment(config)

    pipeline {
        agent { label edgex.mainNode(config) }
        triggers { cron('H 23 * * 5') }
        options {
            timestamps()
        }
        parameters {
            choice(name: 'TEST_STRATEGY', choices: ['All', 'PerfMetrics'])
            choice(name: 'TEST_ARCH', choices: ['All', 'x86_64', 'arm64'])
        }
        environment {
            // Define compose and taf-commom images
            TAF_COMMON_IMAGE_AMD64 = 'nexus3.edgexfoundry.org:10003/edgex-taf-common:latest'
            TAF_COMMON_IMAGE_ARM64 = 'nexus3.edgexfoundry.org:10003/edgex-taf-common-arm64:latest'
            COMPOSE_IMAGE_AMD64 = 'nexus3.edgexfoundry.org:10003/edgex-devops/edgex-compose:latest'
            COMPOSE_IMAGE_ARM64 = 'nexus3.edgexfoundry.org:10003/edgex-devops/edgex-compose-arm64:latest'
        }
        stages {
            stage ('Run Test') {
                parallel {
                    stage ('Collect Performance Metrics on amd64') {
                        when { 
                            expression { params.TEST_STRATEGY == 'All' || params.TEST_STRATEGY == 'PerfMetrics' }
                            expression { params.TEST_ARCH == 'All' || params.TEST_ARCH == 'x86_64' }
                        }
                        environment {
                            ARCH = 'x86_64'
                            NODE = edgex.getNode(config, 'amd64')
                            TAF_COMMON_IMAGE = "${TAF_COMMON_IMAGE_AMD64}"
                            COMPOSE_IMAGE = "${COMPOSE_IMAGE_AMD64}"
                            SECURITY_SERVICE_NEEDED = false
                        }
                        steps {
                            script {
                                collectPerMetricsTest()
                            }
                        }
                    }
                    stage ('Collect Performance Metrics on arm64') {
                        when { 
                            expression { params.TEST_STRATEGY == 'All' || params.TEST_STRATEGY == 'PerfMetrics' }
                            expression { params.TEST_ARCH == 'All' || params.TEST_ARCH == 'arm64' }
                        }
                        environment {
                            ARCH = 'arm64'
                            NODE = edgex.getNode(config, 'arm64')
                            TAF_COMMON_IMAGE = "${TAF_COMMON_IMAGE_ARM64}"
                            COMPOSE_IMAGE = "${COMPOSE_IMAGE_ARM64}"
                            SECURITY_SERVICE_NEEDED = false
                        }
                        steps {
                            script {
                                collectPerMetricsTest()
                            }
                        }
                    }
                }
            }
            stage ('Publish Robotframework Report...') {
                steps{
                    script {
                        // unstash reports
                        catchError { unstash "perf-metrics-x86_64-report" }
                        catchError { unstash "perf-metrics-arm64-report" }

                        dir ('TAF/testArtifacts/reports/stash-report/') {
                            if (("${params.TEST_STRATEGY}" == 'All' || "${params.TEST_STRATEGY}" == 'PerfMetrics')) {
                                DETAIL_LOGFILES= sh (
                                    script: 'ls perf-metrics-*log.html | sed ":a;N;s/\\n/,/g;ta"',
                                    returnStdout: true
                                )
                                publishHTML(
                                    target: [
                                        allowMissing: false,
                                        alwaysLinkToLastBuild: false,
                                        keepAll: true,
                                        reportDir: '.',
                                        reportFiles: "${DETAIL_LOGFILES}",
                                        reportName: 'Performance Metrics Detail Reports'])

                                SUMMARY_LOGFILES= sh (
                                    script: 'ls perf-metrics-*summary.html | sed ":a;N;s/\\n/,/g;ta"',
                                    returnStdout: true
                                )
                                publishHTML(
                                    target: [
                                        allowMissing: false,
                                        alwaysLinkToLastBuild: false,
                                        keepAll: true,
                                        reportDir: '.',
                                        reportFiles: "${SUMMARY_LOGFILES}",
                                        reportName: 'Performance Metrics Summary Reports'])
                            }
                        }
                    }
                    junit 'TAF/testArtifacts/reports/stash-report/**.xml'
                }                                         
            }
        }
    }
} 

def collectPerMetricsTest() {
    catchError {
        timeout(time: 40, unit: 'MINUTES') {
            def rootDir = pwd()
            def runCollecteMetricsScripts = load "${rootDir}/runCollecteMetricsScripts.groovy"
            runCollecteMetricsScripts.main()
        }
    }      
}
