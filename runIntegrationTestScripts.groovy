def main() {
    def BRANCHES = "${BRANCHLIST}".split(',')
    def USE_SECURITY = '-'
    def runbranchstage = [:]

    for (x in BRANCHES) {
        if ("${SECURITY_SERVICE_NEEDED}" == 'true') {
            USE_SECURITY = '-security-'
        }

        def BRANCH = x

        runbranchstage["IntegrationTest ${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}"]= {
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

                stage ("Deploy EdgeX - ${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}") {
                    dir ('TAF/utils/scripts/docker') {
                        sh "sh get-compose-file.sh ${USE_DB} ${ARCH} ${USE_SECURITY} pre-release ${COMPOSE_BRANCH}"
                    }

                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                            -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} \
                            -e USE_DB=${USE_DB} --security-opt label:disable \
                            -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                            --exclude Skipped --include deploy-base-service -u deploy.robot -p default"
                }

                stage ("Run Tests Script - ${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}") {
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                            --security-opt label:disable -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} \
                            -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} -v /tmp/edgex/secrets:/tmp/edgex/secrets:z \
                            -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                            --exclude Skipped -u integrationTest -p device-virtual"
                }

                stage ("Stash Report - ${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}") {
                    echo '===== Merge Reports ====='
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                                -e COMPOSE_IMAGE=${COMPOSE_IMAGE} ${TAF_COMMON_IMAGE} \
                                rebot --inputdir TAF/testArtifacts/reports/edgex \
                                --outputdir TAF/testArtifacts/reports/integration-${BRANCH}-report"

                    dir ("TAF/testArtifacts/reports/integration-${BRANCH}-report") {
                        // Check if the merged-report folder exists
                        def mergeExist = sh (
                            script: 'ls ../ | grep merged-report',
                            returnStatus: true
                        )
                        if (mergeExist != 0) {
                            sh 'mkdir ../merged-report'
                        }
                        //Copy log file to merged-report folder
                        sh "cp log.html ../merged-report/integration-${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}-log.html"
                        sh "cp result.xml ../merged-report/integration-${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}-report.xml"
                    }
                    stash name: "integration-${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}-report", includes: "TAF/testArtifacts/reports/merged-report/*", allowEmpty: true
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
