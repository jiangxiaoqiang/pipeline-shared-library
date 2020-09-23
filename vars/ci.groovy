#!groovy

def call(String type, Map map) {
    if (type == "gradle") {
        pipeline {
            agent(map.agent==null ? "any" : map.agent)
            parameters {
                choice(name: 'env', choices: "fat\nuat\npro", description: 'fat:测试环境部署\nuat:演示环境部署\npro:生产环境部署')
                string(name: 'repoBranch', defaultValue: "${map.repoBranch}", description: 'git分支名称')
                string(name: 'repoUrl', defaultValue: "${map.repoUrl}", description: '项目仓库的地址')
                string(name: 'appName', defaultValue: "${map.appName}", description: '应用的名称，打包Docker镜像时以此命名')
                string(name: 'k8sSvcName', defaultValue: "${map.k8sSvcName}", description: 'Kubernetes服务的名称，不要超过24个字符（实际使用时根据Kubernetes服务名称的规定）')
                string(name: 'tag', defaultValue: "${map.tag}", description: '版本标签，镜像标签')
                string(name: 'k8sResourceType', defaultValue: "${map.k8sResourceType}", description: 'Kubernetes资源类型，如Deployment、StatefulSet等等')
                string(name: 'runUnitTest', defaultValue: "${map.runUnitTest}", description: '是否运行单元测试')
            }
            tools {
                gradle "Gradle"
            }
            environment {
                GRADLE_HOME = "${tool 'Gradle'}"
                PATH = "${env.GRADLE_HOME}/bin:${env.PATH}"
                repoUrl = "${map.repoUrl}"
                registryAddr = getRegistryAddr("${params.env == null}" ? "fat" : "${params.env}",map)
                k8sResourceType = getKubernetesResourceType("${map.k8sResourceType}")
            }

            stages {
                stage('checkout-source') {
                    steps {
                        git branch: "${map.repoBranch}",
                                credentialsId: "${map.credentialsId}",
                                url: "${repoUrl}"
                    }
                }

                stage('unit-test') {
                    when {
                        expression {
                            "${params.runUnitTest}" == '1'
                        }
                    }
                    steps {
                        sh "./gradlew test"
                    }
                }

                stage('build') {
                    steps {
                        sh "./gradlew :${map.appName}:${map.appName}-service:build"
                    }
                }

                stage('package-image') {
                    steps {
                        sh "docker build -f ./Dockerfile -t=\"${params.env}/${map.appName}:v1.0.0\" ."
                    }
                }

                stage('push-image') {
                    steps {
                        sh "docker tag ${params.env}/${map.appName}:${map.tag} ${registryAddr}/${params.env}/${map.appName}:${map.tag}"
                        sh "docker push ${registryAddr}/${params.env}/${map.appName}:${map.tag}"
                    }
                }

                stage('rolling-update-fat') {
                    when {
                        expression {
                            "${params.env}" == 'fat'
                        }
                    }
                    steps {
                        sh "kubectl rollout restart ${k8sResourceType} ${k8sSvcName} -n ${params.env}"
                    }
                }

                stage('rolling-update-pro') {
                    when {
                        expression {
                            "${params.env}" == 'pro'
                        }
                    }
                    steps {
                        sh "b=\" sha256:\""
                        sh "c=\" size:\""
                        sh "start_index=\$(awk -v a=\"$a\" -v b=\"$b\" 'BEGIN{print index(a,b)}')"
                        sh "end_index=\$(awk -v a=\"$a\" -v b=\"$c\" 'BEGIN{print index(a,b)}')"
                        sh "digest=${a:start_index:end_index - start_index}"
                        sh "/Users/dabaidabai/.jenkins/workspace/build_shell/update_harbor_image-statefulset.sh ${k8sResourceType} ${harbor} ${digest} ${params.env}\n"
                    }
                }
            }
        }
    }
}

/**
 * 获取不同部署环境的容器推送地址
 *
 * @param env
 * @param map
 * @return
 */
def getRegistryAddr(env,Map map) {
    if ("pro".equals(env)) {
        return "${map.proRegistryAddr}";
    } else if ("fat".equals(env)) {
        return "${map.fatRegistryAddr}";
    } else if("uat".equals(env)){
        return "${map.uatRegistryAddr}";
    }
}

/**
 * 获取Kubernetes资源类型
 * @param value
 * @return
 */
def getKubernetesResourceType(value) {
    return value == null ? "Deployment" : value
}