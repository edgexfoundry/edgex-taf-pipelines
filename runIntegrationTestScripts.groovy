def main() {
    def USE_SECURITY = '-'
    def runbranchstage = [:]

    if ("${SECURITY_SERVICE_NEEDED}" == 'true') {
        USE_SECURITY = '-security-'
    }

    runbranchstage["IntegrationTest ${ARCH}${USE_SECURITY}${TAF_BRANCH_NAME}"]= {
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

            stage ("Retrieve Compose File - ${ARCH}${USE_SECURITY}${TAF_BRANCH_NAME}") {
                dir ('TAF/utils/scripts/docker') {
                    if ("${TAF_BRANCH_NAME}" != 'main') {
                        sh "sh get-compose-file.sh ${COMPOSE_BRANCH} ${USE_SECURITY} integration-test"
                    } else {
                        sh "sh get-compose-file.sh  ${ARCH} ${USE_SECURITY} ${COMPOSE_BRANCH} integration-test"
                    }
                }
            }

            stage ("Deploy EdgeX - ${ARCH}${USE_SECURITY}${TAF_BRANCH_NAME}") {
                def deployLog= sh (
                    script: "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                    -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} \
                    --security-opt label:disable -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                    --exclude Skipped --include deploy-base-service -t deploy.robot -cd default --name deploy \
                    -o deploy",
                    returnStdout: true
                )
                deploySuccess = sh (
                    script: "echo '$deployLog' | grep '1 passed'",
                    returnStatus: true
                )
                sh "docker images"
            }

            if ( deploySuccess == 0 ) {
                stage ("Run Tests Script - ${ARCH}${USE_SECURITY}${TAF_BRANCH_NAME}") {
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                        --security-opt label:disable -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} \
                        -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} \
                        -v /tmp/edgex/secrets:/tmp/edgex/secrets:z -v /var/run/docker.sock:/var/run/docker.sock \
                        --env-file ${env.WORKSPACE}/TAF/utils/scripts/docker/common-taf.env ${TAF_COMMON_IMAGE} \
                        --exclude Skipped --exclude DelayedStart -t integrationTest -cd device-virtual --name integrationTest \
                        -o integrationTest --no-cleanup"
                }
            }

            stage ("Shutdown EdgeX - ${ARCH}${USE_SECURITY}${TAF_BRANCH_NAME}") {
                sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                    -e COMPOSE_IMAGE=${COMPOSE_IMAGE} --security-opt label:disable \
                    -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                    --exclude Skipped --include shutdown-edgex -t shutdown.robot -cd default --name shutdown \
                    -o shutdown --no-cleanup"
            }

            // Delayed Start
            if ("${SECURITY_SERVICE_NEEDED}" == 'true') {
                stage ("Retrieve Compose File - ${ARCH}${USE_SECURITY}${TAF_BRANCH_NAME}") {
                    dir ('TAF/utils/scripts/docker') {
                        if ("${TAF_BRANCH_NAME}" != 'heads/main') {
                        sh "sh get-compose-file.sh ${COMPOSE_BRANCH} ${USE_SECURITY} integration-test true"
                        } else {
                            sh "sh get-compose-file.sh  ${ARCH} ${USE_SECURITY} ${COMPOSE_BRANCH} integration-test true"
                        }
                    }
                }

                stage ("Deploy EdgeX - Delayed Start - ${ARCH}${USE_SECURITY}${TAF_BRANCH_NAME}") {
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
                    stage ("Run Tests Script - Delayed Start - ${ARCH}${USE_SECURITY}${TAF_BRANCH_NAME}") {
                        sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                            --security-opt label:disable -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} \
                            -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} \
                            -v /var/run/docker.sock:/var/run/docker.sock -v /tmp/edgex/secrets:/tmp/edgex/secrets:z \
                            --env-file ${env.WORKSPACE}/TAF/utils/scripts/docker/common-taf.env ${TAF_COMMON_IMAGE} \
                            --exclude Skipped --include DelayedStart -t integrationTest -cd device-virtual --name delayed-start-test \
                            -o delayed-start --no-cleanup"
                    }
                }

                stage ("Shutdown EdgeX - Delayed Start - ${ARCH}${USE_SECURITY}${TAF_BRANCH_NAME}") {
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                        -e COMPOSE_IMAGE=${COMPOSE_IMAGE} --security-opt label:disable \
                        -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                        --exclude Skipped --include shutdown-edgex -t shutdown.robot -cd default --name delayed-start-shutdown \
                        -o delayed-start-shutdown --no-cleanup"
                }
            }

            stage ("Stash Report - ${ARCH}${USE_SECURITY}${TAF_BRANCH_NAME}") {
                echo '===== Merge Reports ====='
                sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                    -e COMPOSE_IMAGE=${COMPOSE_IMAGE} ${TAF_COMMON_IMAGE} \
                    rebot TAF/testArtifacts/reports TAF/testArtifacts/reports/merged-report"

                dir ("TAF/testArtifacts/reports/merged-report") {
                    //Rename log and result files
                    sh "sudo mv log.html integration-${ARCH}${USE_SECURITY}${TAF_BRANCH_NAME}-log.html"
                    sh "sudo mv result.xml integration-${ARCH}${USE_SECURITY}${TAF_BRANCH_NAME}-report.xml"
                }
                stash name: "integration-${ARCH}${USE_SECURITY}report", includes: "TAF/testArtifacts/reports/merged-report/*", allowEmpty: true
            }
        }
    }
    parallel runbranchstage
}

return this
