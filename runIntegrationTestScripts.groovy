def main() {
    def USE_SECURITY = '-'
    def runbranchstage = [:]

    if ("${SECURITY_SERVICE_NEEDED}" == 'true') {
        USE_SECURITY = '-security-'
    }

    runbranchstage["IntegrationTest ${ARCH}${USE_SECURITY}${TAF_BRANCH}"]= {
        node("${NODE}") {
            stage ('Checkout edgex-taf repository') {
                checkout([$class: 'GitSCM',
                    branches: [[name: "refs/${TAF_BRANCH}"]],
                    doGenerateSubmoduleConfigurations: false,
                    extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: '']],
                    submoduleCfg: [],
                    userRemoteConfigs: [[url: 'https://github.com/cherrycl/edgex-taf.git']]
                ])
            }
            stage ("Retrieve Compose File - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                dir ('TAF/utils/scripts/docker') {
                    sh "sh get-compose-file.sh ${ARCH} ${USE_SECURITY} ${COMPOSE_BRANCH} integration-test"
                }
            }

            stage ("Deploy EdgeX - Redis Bus - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                    -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} \
                    --security-opt label:disable -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                    --exclude Skipped --include deploy-base-service -u deploy.robot -p default"
            }

            stage ("Run Tests Script - Redis Bus - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                    --security-opt label:disable -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} \
                    -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} -v /tmp/edgex/secrets:/tmp/edgex/secrets:z \
                    -v /var/run/docker.sock:/var/run/docker.sock \
                    --env-file ${env.WORKSPACE}/TAF/utils/scripts/docker/common-taf.env ${TAF_COMMON_IMAGE} \
                    --exclude Skipped --include MessageQueue=redis -u integrationTest -p device-virtual"

                dir ('TAF/testArtifacts/reports/rename-report') {
                    sh "cp ../edgex/log.html redis-bus-log.html"
                    sh "cp ../edgex/report.xml redis-bus-report.xml"
                }
            }

            stage ("Shutdown EdgeX - Redis Bus - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                    -e COMPOSE_IMAGE=${COMPOSE_IMAGE} --security-opt label:disable \
                    -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                    --exclude Skipped --include shutdown-edgex -u shutdown.robot -p default"
            }

            stage ("Deploy EdgeX - MQTT Bus - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                    -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} \
                    --security-opt label:disable -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                    --exclude Skipped --include mqtt-bus -u deploy.robot -p default"
            }

            stage ("Run Tests Script - MQTT Bus - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                    --security-opt label:disable -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} \
                    -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} -v /tmp/edgex/secrets:/tmp/edgex/secrets:z \
                    -v /var/run/docker.sock:/var/run/docker.sock \
                    --env-file ${env.WORKSPACE}/TAF/utils/scripts/docker/common-taf.env ${TAF_COMMON_IMAGE} \
                    --exclude Skipped --include MessageQueue=MQTT -u integrationTest -p device-virtual"

                dir ('TAF/testArtifacts/reports/rename-report') {
                    sh "cp ../edgex/log.html mqtt-bus-log.html"
                    sh "cp ../edgex/report.xml mqtt-bus-report.xml"
                }
            }

            stage ("Shutdown EdgeX - MQTT Bus - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                    -e COMPOSE_IMAGE=${COMPOSE_IMAGE} --security-opt label:disable \
                    -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                    --exclude Skipped --include shutdown-edgex -u shutdown.robot -p default"
            }

            stage ("Stash Report - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                echo '===== Merge Reports ====='
                sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                    -e COMPOSE_IMAGE=${COMPOSE_IMAGE} ${TAF_COMMON_IMAGE} \
                    rebot --inputdir TAF/testArtifacts/reports/rename-report \
                    --outputdir TAF/testArtifacts/reports/integration-report"

                dir ("TAF/testArtifacts/reports/integration-report") {
                    // Check if the merged-report folder exists
                    def mergeExist = sh (
                        script: 'ls ../ | grep merged-report',
                        returnStatus: true
                    )
                    if (mergeExist != 0) {
                        sh 'mkdir ../merged-report'
                    }
                    //Copy log file to merged-report folder
                    sh "cp log.html ../merged-report/integration-${ARCH}${USE_SECURITY}log.html"
                    sh "cp result.xml ../merged-report/integration-${ARCH}${USE_SECURITY}report.xml"
                }
                stash name: "integration-${ARCH}${USE_SECURITY}report", includes: "TAF/testArtifacts/reports/merged-report/*", allowEmpty: true
            }
        }
    }
    parallel runbranchstage
}

return this
