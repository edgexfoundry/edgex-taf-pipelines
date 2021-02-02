def main() {
    def BRANCHES = "${BRANCHLIST}".split(',')
    def USE_SECURITY = '-'
    def runbranchstage = [:]

    for (x in BRANCHES) {
        if ("${SECURITY_SERVICE_NEEDED}" == 'true') {
            USE_SECURITY = '-security-'
        }

        def BRANCH = x

        runbranchstage["SmokeTest ${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}"]= {
            node("${NODE}") {
                stage ('Checkout edgex-taf repository') {
                    checkout([$class: 'GitSCM',
                        branches: [[name: "*/${BRANCH}"]],
                        doGenerateSubmoduleConfigurations: false, 
                        extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: '']], 
                        submoduleCfg: [], 
                        userRemoteConfigs: [[url: 'https://github.com/edgexfoundry/edgex-taf.git']]
                        ])
                }

                stage ("Deploy EdgeX - ${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}") {
                    dir ('TAF/utils/scripts/docker') {
                        sh "sh get-compose-file.sh ${USE_DB} ${ARCH} ${USE_SECURITY} nightly-build ${params.SHA1}"
                        sh 'ls *.yml *.yaml'
                    }

                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                            -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} \
                            -e USE_DB=${USE_DB} --security-opt label:disable \
                            -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                            --exclude Skipped --include deploy-base-service -u deploy.robot -p default"
                }

                stage ("Run Functional Tests Script - ${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}") {
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                                    -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} --security-opt label:disable \
                                    -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                                    --exclude Skipped --include deploy-device-service -u deploy.robot -p device-virtual"

                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                            --security-opt label:disable -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} \
                            -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} \
                            -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                            --exclude Skipped --include SmokeTest -u functionalTest -p device-virtual"
                    
                    dir ('TAF/testArtifacts/reports/rename-report') {
                        sh "cp ../edgex/log.html functional-log.html"
                        sh "cp ../edgex/report.xml functional-report.xml"
                    }
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                                    -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} --security-opt label:disable \
                                    -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                                    --exclude Skipped --include shutdown-device-service -u shutdown.robot -p device-virtual"
                }

                stage ("Run Integration Tests Script - ${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}") {
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                            --security-opt label:disable -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} \
                            -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} \
                            -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                            --exclude Skipped --include SmokeTest -u integrationTest -p device-virtual"
                
                    dir ('TAF/testArtifacts/reports/rename-report') {
                        sh "cp ../edgex/log.html integration-log.html"
                        sh "cp ../edgex/report.xml integration-report.xml"
                    }
                }

                stage ("Stash Report - ${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}") {
                    echo '===== Merge Reports ====='
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                                -e COMPOSE_IMAGE=${COMPOSE_IMAGE} ${TAF_COMMON_IMAGE} \
                                rebot --inputdir TAF/testArtifacts/reports/rename-report \
                                --outputdir TAF/testArtifacts/reports/smoke-${BRANCH}-report"

                    dir ("TAF/testArtifacts/reports/smoke-${BRANCH}-report") {
                        // Check if the merged-report folder exists
                        def mergeExist = sh (
                            script: 'ls ../ | grep merged-report',
                            returnStatus: true
                        )
                        if (mergeExist != 0) {
                            sh 'mkdir ../merged-report'
                        }
                        //Copy log file to merged-report folder
                        sh "cp log.html ../merged-report/smoke-${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}-log.html"
                        sh "cp result.xml ../merged-report/smoke-${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}-report.xml"
                    }
                    stash name: "smoke-${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}-report", includes: "TAF/testArtifacts/reports/merged-report/*", allowEmpty: true
                }

                stage ("Shutdown EdgeX - ${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}") {
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                            -e COMPOSE_IMAGE=${COMPOSE_IMAGE} --security-opt label:disable \
                            -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                            --exclude Skipped --include shutdown-edgex -u shutdown.robot -p default"
                }                             
            }
        }
    }
    parallel runbranchstage
}

return this
