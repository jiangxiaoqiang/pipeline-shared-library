#!groovy

def call(String type, Map map) {
    if (type == "gradle") {
        pipeline {
            agent any
            //triggers {
            //    parameterizedCron(env.BRANCH_NAME != 'master' ? '''H/1 * * * * % ABC=XYZ''' : '')
            //}
            parameters {
                choice(
                        name: 'env',
                        choices: ['fat', 'uat', 'pro'],
                        description: 'fat:测试环境部署\nuat:演示环境部署\npro:生产环境部署'
                )
                string(name: 'repoBranch', defaultValue: "${map.repoBranch}", description: 'git分支名称')
                string(name: 'repoUrl', defaultValue: "${map.repoUrl}", description: '项目仓库的地址')
                string(name: 'appName', defaultValue: "${map.appName}", description: '应用的名称，打包Docker镜像时以此命名')
                string(name: 'k8sSvcName', defaultValue: "${map.k8sSvcName}", description: 'Kubernetes服务的名称，不要超过24个字符（实际使用时根据Kubernetes服务名称的规定）')
                string(name: 'tag', defaultValue: "${map.tag}", description: '版本标签，镜像标签')
                string(name: 'k8sResourceType', defaultValue: "${map.k8sResourceType}", description: 'Kubernetes资源类型，如Deployment、StatefulSet等等')
                string(name: 'runUnitTest', defaultValue: "${map.runUnitTest}", description: '是否运行单元测试')
                string(name: 'k8sNamespace', defaultValue: "${map.k8sNamespace}", description: 'Kubernetes命名空间')
                string(name: 'credentialsId', defaultValue: "${map.credentialsId}", description: 'Jenkins认证凭据ID，用于获取原始码')
                string(name: 'fatRegistryAddr', defaultValue: "${map.fatRegistryAddr}", description: 'FAT环境注册地址')
                string(name: 'multibrachComposeName', defaultValue: "${map.multibrachComposeName}", description: '' +
                        '多分支构建时，分支组合名称，例如项目的名字是dolphin，有一个hotfix分支，在多分支构建时，传入Jenkins自动生成的名称dolphin_hotfix')
                string(name: 'pubRepoUrl', defaultValue: "${map.pubRepoUrl}", description: 'Jar包发布的仓库地址')
                string(name: 'buildJar', defaultValue: "${map.buildJar}", description: '构建目标Jar包名称')
            }
            tools {
                gradle "Gradle"
            }
            environment {
                GRADLE_HOME = "${tool 'Gradle'}"
                PATH = "${env.GRADLE_HOME}/bin:${env.PATH}"
                repoUrl = "${map.repoUrl}"
                registryAddr = getRegistryAddr("${params.env}" == null ? "fat" : "${params.env}", map)
                k8sResourceType = getKubernetesResourceType("${params.k8sResourceType}")
            }

            stages {
                stage('checkout-source') {
                    steps {
                        git branch: "${params.repoBranch}",
                                credentialsId: "${params.credentialsId}",
                                url: "${params.repoUrl}"
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

                stage('build-api') {
                    steps {
                        script {
                            try {
                                sh "./gradlew :${params.multibrachComposeName == null ? params.appName : params.multibrachComposeName}:${params.appName}-api:build publishMavenPublicationToMavenRepository -x test"
                            } catch (err) {
                                echo "Publish jar failed, but will be continue," + err.getMessage()
                            }
                        }
                    }
                }

                stage('build') {
                    steps {
                        sh "./gradlew :${params.multibrachComposeName == null ? params.appName : params.multibrachComposeName}:${params.appName}-service:build -x test"
                    }
                }

                stage('prune-image'){
                    steps{
                        sh "docker system prune"
                    }
                }

                stage('package-image') {
                    steps {
                        sh "docker build -f ./Dockerfile --build-arg buildJar=\"${params.buildJar}\" -t=\"${params.appName}:${params.tag}\" ."
                    }
                }

                stage('tag-image') {
                    steps {
                        sh "docker tag ${params.appName}:${params.tag} ${registryAddr}/${params.appName}:${params.tag}"
                    }
                }

                stage('push-image') {
                    steps {
                        sh "docker push ${registryAddr}/${params.appName}:${params.tag}"
                    }
                }

                stage('rolling-update-fat') {
                    when {
                        expression {
                            "${params.env}" == 'fat'
                        }
                    }
                    steps {
                        sh "kubectl rollout restart ${k8sResourceType} ${k8sSvcName} -n ${params.k8sNamespace}"
                    }
                }

                stage('rolling-update-uat') {
                    when {
                        expression {
                            "${params.env}" == 'uat'
                        }
                    }
                    steps {
                        sh "kubectl rollout restart ${k8sResourceType} ${k8sSvcName} -n ${params.k8sNamespace}"
                    }
                }

                stage('rolling-update-pro') {
                    when {
                        expression {
                            "${params.env}" == 'pro'
                        }
                    }
                    steps {
                        sh "/Users/dabaidabai/.jenkins/workspace/build_shell/update-production-pod.sh ${params.tag} ${k8sSvcName} ${params.k8sNamespace} ${params.appName} ${k8sResourceType}"
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
def getRegistryAddr(env, Map map) {
    print("choice:" + env)
    if ("pro" == env) {
        return "${map.proRegistryAddr}"
    }
    if ("fat" == env) {
        print("fataddress:" + "${map.fatRegistryAddr}")
        return "${map.fatRegistryAddr}"
    }
    if ("uat" == env) {
        return "${map.uatRegistryAddr}"
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


