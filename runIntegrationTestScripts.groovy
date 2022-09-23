def main() {
    def USE_SECURITY = '-'
    def runbranchstage = [:]
    def BUSES

    if ("${SECURITY_SERVICE_NEEDED}" == 'true') {
        USE_SECURITY = '-security-'
    }

    if ("${TEST_BUS}" == 'All') {
        BUSES = "REDIS,MQTT".split(',')
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
                    // Set deploy_tag by Messagebus
                    if ( BUS == 'REDIS' ) {
                        deploy_tag = 'deploy-base-service'
                    } else {
                        deploy_tag = 'mqtt-bus'
                    }

                    stage ("Deploy EdgeX - ${BUS} Bus - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                        def deployLog= sh (
                            script: "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                            -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} \
                            --security-opt label:disable -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                            --exclude Skipped --include ${deploy_tag} -u deploy.robot -p default --name ${BUS}-bus-deploy",
                            returnStdout: true
                        )
                        deploySuccess = sh (
                            script: "echo '$deployLog' | grep '1 passed'",
                            returnStatus: true
                        )
                        dir ("TAF/testArtifacts/reports/rename-report") {
                            sh "cp ../edgex/log.html ${BUS}-bus-deploy-log.html"
                            sh "cp ../edgex/report.xml ${BUS}-bus-deploy-report.xml"
                        }
                    }

                    if ( deploySuccess == 0 ) {
                        stage ("Run Tests Script - ${BUS} Bus - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                            sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                                --security-opt label:disable -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} \
                                -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} -v /tmp/edgex/secrets:/tmp/edgex/secrets:z \
                                -v /var/run/docker.sock:/var/run/docker.sock \
                                --env-file ${env.WORKSPACE}/TAF/utils/scripts/docker/common-taf.env ${TAF_COMMON_IMAGE} \
                                --exclude Skipped --include MessageQueue=${BUS} -u integrationTest -p device-virtual --name ${BUS}-bus"

                            dir ('TAF/testArtifacts/reports/rename-report') {
                                sh "cp ../edgex/log.html ${BUS}-bus-log.html"
                                sh "cp ../edgex/report.xml ${BUS}-bus-report.xml"
                            }
                        }
                    }

                    stage ("Shutdown EdgeX - ${BUS} Bus - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                        sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                            -e COMPOSE_IMAGE=${COMPOSE_IMAGE} --security-opt label:disable \
                            -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                            --exclude Skipped --include shutdown-edgex -u shutdown.robot -p default --name ${BUS}-bus-shutdown"
                        
                        dir ("TAF/testArtifacts/reports/rename-report") {
                            sh "cp ../edgex/log.html ${BUS}-bus-shutdown-log.html"
                            sh "cp ../edgex/report.xml ${BUS}-bus-shutdown-report.xml"
                        }
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
                        --exclude Skipped --include deploy-base-service -u deploy.robot -p default --name delayed-start-deploy",
                        returnStdout: true
                    )
                    deploySuccess = sh (
                        script: "echo '$deployLog' | grep '1 passed'",
                        returnStatus: true
                    )
                    dir ("TAF/testArtifacts/reports/rename-report") {
                        sh "cp ../edgex/log.html delayed-start-deploy-log.html"
                        sh "cp ../edgex/report.xml delayed-start-deploy-report.xml"
                    }
                }

                if ( deploySuccess == 0 ) {
                    stage ("Run Tests Script - Delayed Start - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                        sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                            --security-opt label:disable -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} \
                            -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} -v /tmp/edgex/secrets:/tmp/edgex/secrets:z \
                            -v /var/run/docker.sock:/var/run/docker.sock \
                            --env-file ${env.WORKSPACE}/TAF/utils/scripts/docker/common-taf.env ${TAF_COMMON_IMAGE} \
                            --exclude Skipped --include DelayedStart -u integrationTest -p device-virtual --name delayed-start-test"

                        dir ('TAF/testArtifacts/reports/rename-report') {
                            sh "cp ../edgex/log.html delayed-start-log.html"
                            sh "cp ../edgex/report.xml delayed-start-report.xml"
                        }
                    }
                }

                stage ("Shutdown EdgeX - Delayed Start - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                        -e COMPOSE_IMAGE=${COMPOSE_IMAGE} --security-opt label:disable \
                        -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                        --exclude Skipped --include shutdown-edgex -u shutdown.robot -p default --name delayed-start-shutdown"
                    
                    dir ("TAF/testArtifacts/reports/rename-report") {
                        sh "cp ../edgex/log.html delayed-start-shutdown-log.html"
                        sh "cp ../edgex/report.xml delayed-start-shutdown-report.xml"
                    }
                }
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
