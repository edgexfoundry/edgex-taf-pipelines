
def main() {
    def PROFILES = "${PROFILELIST}".split(',')
    def USE_SECURITY = '-'
    def runbranchstage = [:]
        
    if ("${SECURITY_SERVICE_NEEDED}" == 'true') {
        USE_SECURITY = '-security-'
    }

    runbranchstage["Test ${ARCH}${USE_SECURITY}${TAF_BRANCH}"]= {
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

            stage ("Deploy EdgeX - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                dir ('TAF/utils/scripts/docker') {
                    sh "sh get-compose-file.sh  ${ARCH} ${USE_SECURITY} ${COMPOSE_BRANCH} funcational-test"
                }

                def deployLog = sh (
                    script: "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z \
                            -w ${env.WORKSPACE} -e COMPOSE_IMAGE=${COMPOSE_IMAGE} --security-opt label:disable \
                            -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} \
                            -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                            --exclude Skipped --include deploy-base-service -t deploy.robot -cd default --name deploy -o deploy-edgex",
                    returnStdout: true
                )
                deploySuccess = sh (
                    script: "echo '$deployLog' | grep '1 passed'",
                    returnStatus: true
                )
            }
            
            if ( deploySuccess == 0 ) {
                stage ("Run API Tests - ${ARCH}${USE_SECURITY}${TAF_BRANCH}"){
                    echo "===== Run API Tests ====="
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                        -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} -e ARCH=${ARCH} \
                        --env-file ${env.WORKSPACE}/TAF/utils/scripts/docker/common-taf.env \
                        --security-opt label:disable -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                        --exclude Skipped -t functionalTest/API -cd default --name API -o api --no-cleanup"
                }
            

                echo "Profiles : ${PROFILES}"
                stage ("Run Device Service Tests - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                    script {
                        for (y in PROFILES) {
                            def profile = y
                            echo "Profile : ${profile}"
                            echo "===== Run ${profile} Test Case ====="
                            sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                                -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} \
                                -e ARCH=${ARCH} --security-opt label:disable \
                                -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                                --exclude Skipped -t functionalTest/device-service/common -cd ${profile} -o ${profile}-common --no-cleanup"
                        }
                    }
                }
            }

            stage ("Shutdown EdgeX - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                    -e COMPOSE_IMAGE=${COMPOSE_IMAGE} --security-opt label:disable \
                    -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                    --exclude Skipped --include shutdown-edgex -t shutdown.robot -cd default --name shutdown -o shutdown-edgex --no-cleanup"
            }

            stage ("Stash Report - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                echo '===== Merge Reports ====='
                sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                    -e COMPOSE_IMAGE=${COMPOSE_IMAGE} ${TAF_COMMON_IMAGE} \
                    rebot TAF/testArtifacts/reports TAF/testArtifacts/reports/merged-report"

                dir ("TAF/testArtifacts/reports/merged-report") {
                    //Rename log and result files
                    sh "sudo mv log.html ${ARCH}${USE_SECURITY}log.html"
                    sh "sudo mv result.xml ${ARCH}${USE_SECURITY}report.xml"
                }
                stash name: "${ARCH}${USE_SECURITY}report", includes: "TAF/testArtifacts/reports/merged-report/*", allowEmpty: true
            }

            
        }
    }
    parallel runbranchstage
}

return this
