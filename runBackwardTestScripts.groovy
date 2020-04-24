def main() {
    def BRANCHES = "${BRANCHLIST}".split(',')
    def profile = 'device-virtual'
    def USE_SECURITY = '-'

    def runbranchstage = [:]

    for (x in BRANCHES) {
        if ("${SECURITY_SERVICE_NEEDED}" == 'true') {
            USE_SECURITY = '-security-'
        }

        def BRANCH = x
        
        runbranchstage["BackwardTest ${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}"]= {
            node("${SLAVE}") {
                stage ('Checkout edgex-taf repository') {
                    checkout([$class: 'GitSCM',
                        branches: [[name: "*/${BRANCH}"]],
                        doGenerateSubmoduleConfigurations: false, 
                        extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: '']], 
                        submoduleCfg: [], 
                        userRemoteConfigs: [[url: 'https://github.com/edgexfoundry/edgex-taf.git']]
                        ])
                }

                stage ("[Fuji] Deploy EdgeX - ${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}") {
                    dir ('TAF/utils/scripts/docker') {
                        sh "sh get-compose-file.sh ${USE_DB} ${ARCH} ${USE_SECURITY} fuji"
                        sh 'ls *.yml *.yaml'
                    }

                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                            -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} \
                            -e USE_DB=${USE_DB} -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
                            --exclude Skipped --include deploy-base-service -u deploy.robot -p default"
                }

                stage ("[Fuji] Run Tests Script - ${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}") {
                    echo "Profile : ${profile}"
                    echo "===== Deploy ${profile} ====="
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                            -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} \
                            -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
                            --exclude Skipped --include deploy-device-service -u deploy.robot -p ${profile}"

                    echo "===== Run ${profile} Test Case ====="
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                            -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} \
                            -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
                            --exclude Skipped --include Backward -u functionalTest/device-service/common -p ${profile} \
                            --name ${profile}-fuji"
                            
                    dir ('TAF/testArtifacts/reports/rename-report') {
                        sh "cp ../edgex/log.html ${profile}-common-log.html"
                        sh "cp ../edgex/report.xml ${profile}-common-report.xml"
                    }

                    echo "===== Shutdown ${profile} ====="
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                            -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} \
                            -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
                            --exclude Skipped --include shutdown-device-service -u shutdown.robot -p ${profile}"
                }

                stage ("[Fuji] Stop EdgeX - ${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}") {
                    sh 'curl -X DELETE http://localhost:48080/api/v1/event/scruball'
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                            -v /var/run/docker.sock:/var/run/docker.sock ${COMPOSE_IMAGE} \
                            -f ${env.WORKSPACE}/TAF/utils/scripts/docker/docker-compose.yaml stop"
                }
                
                stage ("[Backward] Deploy EdgeX - ${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}") {
                    dir ('TAF/utils/scripts/docker') {
                        sh "sh get-compose-file-backward.sh ${USE_DB} ${ARCH} ${USE_SECURITY} fuji"
                        sh 'ls *.yaml'
                    }

                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                            -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} \
                            -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} -e USE_DB=${USE_DB} \
                            -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
                            --exclude Skipped --include backward -u deploy.robot -p ${profile}"
                }
                
                stage ("[Backward] Run Tests Script - ${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}") {
                    echo "Profile : ${profile}"
                    echo "===== Run ${profile} Test Case ====="
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                            -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} \
                            -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
                            --exclude Skipped --include Backward -u functionalTest/device-service/common -p ${profile} \
                            --name ${profile}-backward"
                            
                    dir ('TAF/testArtifacts/reports/rename-report') {
                            sh "cp ../edgex/log.html backward-${profile}-common-log.html"
                            sh "cp ../edgex/report.xml backward-${profile}-common-report.xml"
                    }
                }
                
                stage ("[Backward] Stash Report - ${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}") {
                    echo '===== Merge Reports ====='
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                                -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
                                rebot --inputdir TAF/testArtifacts/reports/rename-report \
                                --outputdir TAF/testArtifacts/reports/${BRANCH}-report"

                    dir ("TAF/testArtifacts/reports/${BRANCH}-report") {
                        // Check if the merged-report folder exists
                        def mergeExist = sh (
                            script: 'ls ../ | grep merged-report',
                            returnStatus: true
                        )
                        if (mergeExist != 0) {
                            sh 'mkdir ../merged-report'
                        }
                        //Copy log file to merged-report folder
                        sh "cp log.html ../merged-report/backward-${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}-fuji-log.html"
                        sh "cp result.xml ../merged-report/backward-${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}-fuji-report.xml"
                    }
                    stash name: "backward-${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}-fuji-report", includes: "TAF/testArtifacts/reports/merged-report/*", allowEmpty: true
                }
                
                stage ("[Backward] Shutdown EdgeX - ${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}") {
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:rw,z -w ${env.WORKSPACE} \
                            -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} \
                            -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMOM_IMAGE} \
                            --exclude Skipped --include shutdown-edgex -u shutdown.robot -p ${profile}"
                }                             
            }
        }
    }
    parallel runbranchstage
}

return this
