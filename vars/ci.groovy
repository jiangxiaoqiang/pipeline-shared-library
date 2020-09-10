#!groovy
def call(String type,Map map) {
    if (type == "gradle") {
        pipeline {
            agent any
            parameters {
                //固定设置三类pipeline场景
                choice(name: 'scene', choices: "scene1:完整流水线\nscene2:代码检查\nscene3:测试部署", description: '场景选择，默认运行完整流水线，如果只做开发自测可选择代码检查，如果只做环境部署可选择测试部署')
                //repoBranch参数后续替换成git parameter不再依赖手工输入,JENKINS-46451
                string(name: 'repoBranch', defaultValue: "${map.repoBranch}", description: 'git分支名称')
                string(name: 'repoUrl', defaultValue: "${map.repoUrl}", description: 'repoUrl')
                string(name: 'appName', defaultValue: "${map.appName}", description: 'appName')
                string(name: 'k8sSvcName', defaultValue: "${map.k8sSvcName}", description: 'Kubernetes Service Name')
                string(name: 'env', defaultValue: "${map.env}", description: 'env')
                string(name: 'tag', defaultValue: "${map.tag}", description: 'tag')
                string(name: 'k8sResourceType', defaultValue: "${map.k8sResourceType}", description: 'Kubernetes Resource Type')
            }
            tools {
                maven "${map.maven}"
                jdk "${map.jdk}"
            }
            environment {
                repoUrl = "${map.repoUrl}"
                //git服务全系统只读账号，无需修改
                CRED_ID = "${map.CRED_ID}"
                //pom.xml的相对路径
                POM_PATH = "${map.POM_PATH}"

                fatImageRegistryAddr="registry.cn-hangzhou.aliyuncs.com/dabai_app_k8s"
                proImageRegistryAddr=""
            }

            options {
                disableConcurrentBuilds()
                timeout(time: 1, unit: 'HOURS')
                //保持构建的最大个数
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
                    steps{
                        sh "./gradlew :${map.appName}:${map.appName}-service:build -x test"
                    }
                }

                stage('package-image') {
                    steps{
                        sh "docker build -f ./Dockerfile -t=\"${map.env}/${map.appName}-service:v1.0.0\" ."
                    }
                }

                stage('push-image') {
                    steps{
                        sh "docker tag ${map.env}/{map.appName}:${map.tag} ${fatImageRegistryAddr}/${map.env}/${map.appName}:${map.tag}"
                        sh "docker push ${fatImageRegistryAddr}/${map.env}/${map.appName}:${map.tag}"
                    }
                }

                stage('rolling-update') {
                    steps{
                        sh "kubectl rollout restart ${k8sResourceType} ${k8sSvcName} -n ${map.env}"
                    }
                }
            }
        }
    }
}