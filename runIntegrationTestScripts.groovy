def main() {
    def USE_SECURITY = '-'
    def runbranchstage = [:]
    def BUSES

    if ("${SECURITY_SERVICE_NEEDED}" == 'true') {
        USE_SECURITY = '-security-'
    }

    if ("${TEST_BUS}" == 'All') {
        BUSES = "MQTT".split(',')
    } else {
        BUSES = "${TEST_BUS}"
    }

    runbranchstage["IntegrationTest ${ARCH}${USE_SECURITY}${TAF_BRANCH}"]= {
        node("${NODE}") {
            stage ('Checkout edgex-taf repository') {
                checkout([$class: 'GitSCM',
                    branches: [[name: "refs/${TAF_BRANCH}"]],
                    doGenerateSubmoduleConfigurations: false,
                    extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: '']],
                    submoduleCfg: [],
                    userRemoteConfigs: [[url: 'https://github.com/edgexfoundry/edgex-taf.git']]
                ])
            }
            if ( BUSES != 'None' ) {
                for (BUS in BUSES) {
                    stage ("Retrieve Compose File - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                        dir ('TAF/utils/scripts/docker') {
                            sh "sh get-compose-file.sh ${ARCH} ${USE_SECURITY} ${COMPOSE_BRANCH} integration-test"
                        }
                    }

                    stage ("Deploy EdgeX - ${BUS} Bus - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                        def deployLog= sh (
                            script: "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                            -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} \
                            --security-opt label:disable -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                            --exclude Skipped --include deploy-base-service -t deploy.robot -cd default --name ${BUS}-bus-deploy \
                            -o ${BUS}-bus-deploy",
                            returnStdout: true
                        )
                        deploySuccess = sh (
                            script: "echo '$deployLog' | grep '1 passed'",
                            returnStatus: true
                        )
                    }

                    if ( deploySuccess == 0 ) {
                        stage ("Run Tests Script - ${BUS} Bus - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                            sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                                --security-opt label:disable -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} \
                                -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} \
                                -v /tmp/edgex/secrets:/tmp/edgex/secrets:z -v /var/run/docker.sock:/var/run/docker.sock \
                                --env-file ${env.WORKSPACE}/TAF/utils/scripts/docker/common-taf.env ${TAF_COMMON_IMAGE} \
                                --exclude Skipped --exclude DelayedStart -t integrationTest -cd device-virtual --name ${BUS}-bus \
                                -o ${BUS}-bus --no-cleanup"
                        }
                    }

                    stage ("Shutdown EdgeX - ${BUS} Bus - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                        sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                            -e COMPOSE_IMAGE=${COMPOSE_IMAGE} --security-opt label:disable \
                            -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                            --exclude Skipped --include shutdown-edgex -t shutdown.robot -cd default --name ${BUS}-bus-shutdown \
                            -o ${BUS}-bus-shutdown --no-cleanup"
                    }
                }
            }

            // Delayed Start
            if ("${SECURITY_SERVICE_NEEDED}" == 'true') {
                stage ("Retrieve Compose File - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                    dir ('TAF/utils/scripts/docker') {
                        sh "sh get-compose-file.sh ${ARCH} ${USE_SECURITY} ${COMPOSE_BRANCH} integration-test true"
                    }
                }

                stage ("Deploy EdgeX - Delayed Start - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                    def deployLog= sh (
                        script: "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                        -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} \
                        --security-opt label:disable -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                        --exclude Skipped --include deploy-base-service -t deploy.robot -cd default --name delayed-start-deploy \
                        -o delayed-start-deploy --no-cleanup",
                        returnStdout: true
                    )
                    deploySuccess = sh (
                        script: "echo '$deployLog' | grep '1 passed'",
                        returnStatus: true
                    )
                }

                if ( deploySuccess == 0 ) {
                    stage ("Run Tests Script - Delayed Start - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                        sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                            --security-opt label:disable -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} \
                            -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} \
                            -v /var/run/docker.sock:/var/run/docker.sock -v /tmp/edgex/secrets:/tmp/edgex/secrets:z \
                            --env-file ${env.WORKSPACE}/TAF/utils/scripts/docker/common-taf.env ${TAF_COMMON_IMAGE} \
                            --exclude Skipped --include DelayedStart -t integrationTest -cd device-virtual --name delayed-start-test \
                            -o delayed-start --no-cleanup"
                    }
                }

                stage ("Shutdown EdgeX - Delayed Start - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                        -e COMPOSE_IMAGE=${COMPOSE_IMAGE} --security-opt label:disable \
                        -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                        --exclude Skipped --include shutdown-edgex -t shutdown.robot -cd default --name delayed-start-shutdown \
                        -o delayed-start-shutdown --no-cleanup"
                }
            }

            stage ("Stash Report - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                echo '===== Merge Reports ====='
                sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                    -e COMPOSE_IMAGE=${COMPOSE_IMAGE} ${TAF_COMMON_IMAGE} \
                    rebot TAF/testArtifacts/reports TAF/testArtifacts/reports/merged-report"

                dir ("TAF/testArtifacts/reports/merged-report") {
                    //Rename log and result files
                    sh "sudo mv log.html integration-${ARCH}${USE_SECURITY}log.html"
                    sh "sudo mv result.xml integration-${ARCH}${USE_SECURITY}report.xml"
                }
                stash name: "integration-${ARCH}${USE_SECURITY}report", includes: "TAF/testArtifacts/reports/merged-report/*", allowEmpty: true
            }
        }
    }
    parallel runbranchstage
}

return this
