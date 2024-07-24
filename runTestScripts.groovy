
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
                    sh "sh get-compose-file.sh  ${ARCH} ${USE_SECURITY} ${COMPOSE_BRANCH} funcational-test ${REGISTRY_SERVICE}"
                }

                def deployLog = sh (
                    script: "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z \
                            -w ${env.WORKSPACE} -e COMPOSE_IMAGE=${COMPOSE_IMAGE} --security-opt label:disable \
                            -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} -e REGISTRY_SERVICE=$REGISTRY_SERVICE \
                            -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                            --exclude Skipped --include deploy-base-service -u deploy.robot -p default --name deploy",
                    returnStdout: true
                )
                deploySuccess = sh (
                    script: "echo '$deployLog' | grep '1 passed'",
                    returnStatus: true
                )
                dir ("TAF/testArtifacts/reports/rename-report") {
                    sh "cp ../edgex/log.html deploy-edgex-log.html"
                    sh "cp ../edgex/report.xml deploy-edgex-report.xml"
                }
            }
            
            if ( deploySuccess == 0 ) {
                stage ("Run API Tests - ${ARCH}${USE_SECURITY}${TAF_BRANCH}"){
                    echo "===== Run API Tests ====="
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                        -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} -e ARCH=${ARCH} \
                        -e REGISTRY_SERVICE=${REGISTRY_SERVICE} --env-file ${env.WORKSPACE}/TAF/utils/scripts/docker/common-taf.env \
                        --security-opt label:disable -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                        --exclude Skipped -u functionalTest/API -p default --name API"

                    dir ('TAF/testArtifacts/reports/rename-report') {
                        sh "cp ../edgex/log.html api-log.html"
                        sh "cp ../edgex/report.xml api-report.xml"
                    }
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
                                -e ARCH=${ARCH} --security-opt label:disable -e REGISTRY_SERVICE=${REGISTRY_SERVICE} \
                                -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                                --exclude Skipped -u functionalTest/device-service/common -p ${profile}"
                                
                            dir ('TAF/testArtifacts/reports/rename-report') {
                                sh "cp ../edgex/log.html ${profile}-common-log.html"
                                sh "cp ../edgex/report.xml ${profile}-common-report.xml"
                            }
                        }
                    }
                }
            }

            stage ("Shutdown EdgeX - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                    -e COMPOSE_IMAGE=${COMPOSE_IMAGE} --security-opt label:disable \
                    -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                    --exclude Skipped --include shutdown-edgex -u shutdown.robot -p default --name shutdown"

                dir ("TAF/testArtifacts/reports/rename-report") {
                    sh "cp ../edgex/log.html shutdown-edgex-log.html"
                    sh "cp ../edgex/report.xml shutdown-edgex-report.xml"
                }
            }

            stage ("Stash Report - ${ARCH}${USE_SECURITY}${TAF_BRANCH}") {
                echo '===== Merge Reports ====='
                sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                    -e COMPOSE_IMAGE=${COMPOSE_IMAGE} ${TAF_COMMON_IMAGE} \
                    rebot --inputdir TAF/testArtifacts/reports/rename-report \
                    --outputdir TAF/testArtifacts/reports/report"

                dir ("TAF/testArtifacts/reports/report") {
                    // Check if the merged-report folder exists
                    def mergeExist = sh (
                        script: 'ls ../ | grep merged-report',
                        returnStatus: true
                    )
                    if (mergeExist != 0) {
                        sh 'mkdir ../merged-report'
                    }
                    //Copy log file to merged-report folder
                    sh "cp log.html ../merged-report/${ARCH}${USE_SECURITY}log.html"
                    sh "cp result.xml ../merged-report/${ARCH}${USE_SECURITY}report.xml"
                }
                stash name: "${ARCH}${USE_SECURITY}report", includes: "TAF/testArtifacts/reports/merged-report/*", allowEmpty: true
            }

            
        }
    }
    parallel runbranchstage
}

return this
