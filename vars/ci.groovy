#!groovy

def call(String type, Map map) {
    if (type == "gradle") {
        pipeline {
            agent any
            parameters {
                choice(name: 'env', choices: "dabai-fat\ndabai-uat\ndabai-pro", description: 'fat:测试部署\nuat:演示环境部署\npro:生产环境部署')
                string(name: 'repoBranch', defaultValue: "${map.repoBranch}", description: 'git分支名称')
                string(name: 'repoUrl', defaultValue: "${map.repoUrl}", description: 'repoUrl')
                string(name: 'appName', defaultValue: "${map.appName}", description: 'appName')
                string(name: 'k8sSvcName', defaultValue: "${map.k8sSvcName}", description: 'Kubernetes Service Name')
                string(name: 'tag', defaultValue: "${map.tag}", description: 'tag')
                string(name: 'k8sResourceType', defaultValue: "${map.k8sResourceType}", description: 'Kubernetes资源类型')
            }
            tools {
                gradle "${type}"
            }
            environment {
                GRADLE_HOME = "${tool 'Gradle'}"
                PATH = "${env.GRADLE_HOME}/bin:${env.PATH}"
                repoUrl = "${map.repoUrl}"
                registryAddr = getRegistryAddr("${env == null}" ? "dabai-fat" : "${env}")
            }

            options {
                disableConcurrentBuilds()
                timeout(time: 1, unit: 'HOURS')
                buildDisarder(logRotator(numToKeepStr: '10'))
            }
            stages {
                stage('checkout-source') {
                    steps {
                        git branch: "${map.repoBranch}",
                                credentialsId: '3808ec5a-b476-4471-8c47-db66ed4d0eb0',
                                url: "${repoUrl}"
                    }
                }

                stage('build') {
                    steps {
                        sh "./gradlew :${map.appName}:${map.appName}-service:build -x test"
                    }
                }

                stage('package-image') {
                    steps {
                        sh "docker build -f ./Dockerfile -t=\"${env}/${map.appName}-service:v1.0.0\" ."
                    }
                }

                stage('push-image') {
                    steps {
                        sh "docker tag ${env}/{map.appName}:${map.tag} ${registryAddr}/${env}/${map.appName}:${map.tag}"
                        sh "docker push ${registryAddr}/${env}/${map.appName}:${map.tag}"
                    }
                }

                stage('rolling-update-fat') {
                    when {
                        expression {
                            "${env}" == 'dabai-fat'
                        }
                    }
                    steps {
                        sh "kubectl rollout restart ${k8sResourceType} ${k8sSvcName} -n ${env}"
                    }
                }

                stage('rolling-update-pro') {
                    when {
                        expression {
                            "${env}" == 'dabai-pro'
                        }
                    }
                    steps {
                        sh "b=\" sha256:\""
                        sh "c=\" size:\""
                        sh "start_index=\$(awk -v a=\"$a\" -v b=\"$b\" 'BEGIN{print index(a,b)}')"
                        sh "end_index=\$(awk -v a=\"$a\" -v b=\"$c\" 'BEGIN{print index(a,b)}')"
                        sh "digest=${a:start_index:end_index-start_index}"
                        sh "/Users/dabaidabai/.jenkins/workspace/build_shell/update_harbor_image-statefulset.sh ${k8sResourceType} ${harbor} ${digest} ${env}\n"
                    }
                }
            }
        }
    }
}


def getRegistryAddr(env) {
    if ("dabai-pro".equals(env)) {
        return "int";
    } else if ("dabai-fat".equals(env)) {
        return "registry.cn-hangzhou.aliyuncs.com/dabai_app_k8s";
    } else {
        return "registry.cn-hangzhou.aliyuncs.com/dabai_app_k8s";
    }
}