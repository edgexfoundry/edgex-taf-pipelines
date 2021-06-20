def main() {
    def USE_SECURITY = '-'
    
    if ("${SECURITY_SERVICE_NEEDED}" == 'true') {
        USE_SECURITY = '-security-'
    }

    node("${NODE}") {
        stage ('Checkout edgex-taf repository') {
            checkout([$class: 'GitSCM',
                branches: [[name: "*/master"]],
                doGenerateSubmoduleConfigurations: false, 
                extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: '']], 
                submoduleCfg: [], 
                userRemoteConfigs: [[url: 'https://github.com/edgexfoundry/edgex-taf.git']]
                ])
        }

        stage ("Collect Performance Metrics ${USE_SECURITY}${ARCH}") {
            dir ('TAF/utils/scripts/docker') {
                sh "sh get-compose-file-perfermance.sh ${ARCH} ${USE_SECURITY}"
            }

            sh "docker run --rm -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                    -v /var/run/docker.sock:/var/run/docker.sock --security-opt label:disable \
                    ${COMPOSE_IMAGE} -f ${env.WORKSPACE}/TAF/utils/scripts/docker/docker-compose.yml pull"

            sh "docker run --rm --network host --privileged -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                    -e ARCH=${ARCH} -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} --security-opt label:disable \
                    -v /var/run/docker.sock:/var/run/docker.sock -e COMPOSE_IMAGE=${COMPOSE_IMAGE} \
                    ${TAF_COMMON_IMAGE} --exclude Skipped -u performanceTest/performance-metrics-collection \
                    --profile performance-metrics"
        }

        stage ("Stash Report ${USE_SECURITY}${ARCH}") {
            echo '===== rebot Reports ====='
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                                -e COMPOSE_IMAGE=${COMPOSE_IMAGE} ${TAF_COMMON_IMAGE} \
                                rebot --inputdir TAF/testArtifacts/reports/edgex \
                                --outputdir TAF/testArtifacts/reports/rebot-report"

            dir ("TAF/testArtifacts/reports") {
                def folderExist = sh (
                    script: 'ls | grep stash-report',
                    returnStatus: true
                )
                if (folderExist != 0) {
                    sh 'mkdir stash-report'
                }
                // rename reports
                sh "sudo cp rebot-report/log.html stash-report/perf-metrics-${ARCH}${USE_SECURITY}log.html"
                sh "sudo cp rebot-report/result.xml stash-report/perf-metrics-${ARCH}${USE_SECURITY}report.xml"
                sh "sudo cp edgex/performance-metrics.html stash-report/perf-metrics-${ARCH}${USE_SECURITY}summary.html"
            } 
            stash name: "perf-metrics-${ARCH}${USE_SECURITY}report", includes: "TAF/testArtifacts/reports/stash-report/*", allowEmpty: true
        }
    }
}

return this
