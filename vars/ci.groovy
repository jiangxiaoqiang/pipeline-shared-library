#!groovy
def call(String type,Map map) {
    if (type == "maven") {
        pipeline {
            agent any
            //参数化变量,目前只支持[booleanParam, choice, credentials, file, text, password, run, string]这几种参数类型，其他高级参数化类型还需等待社区支持
            parameters {
                //固定设置三类pipeline场景
                choice(name: 'scene', choices: "scene1:完整流水线\nscene2:代码检查\nscene3:测试部署", description: '场景选择，默认运行完整流水线，如果只做开发自测可选择代码检查，如果只做环境部署可选择测试部署')
                //repoBranch参数后续替换成git parameter不再依赖手工输入,JENKINS-46451
                string(name: 'repoBranch', defaultValue: "${map.repoBranch}", description: 'git分支名称')
                //服务器相关参数采用了组合方式，避免多次选择
                choice(name: 'server', choices: "${map.server}", description: '测试服务器列表选择')
                string(name: 'dubboPort', defaultValue: "${map.dubboPort}", description: '测试服务器的dubbo服务端口')
                //单元测试代码覆盖率要求，各项目视要求调整参数
                string(name: 'lineCoverage', defaultValue: "${map.lineCoverage}", description: '单元测试代码覆盖率要求(%)，小于此值pipeline将会失败！')
                //若勾选在pipelie完成后会邮件通知测试人员进行验收
                booleanParam(name: 'isCommitQA', defaultValue: false, description: '是否在pipeline完成后，邮件通知测试人员进行人工验收')
            }
            //环境变量，初始确定后一般不需更改
            tools {
                maven "${map.maven}"
                jdk "${map.jdk}"
            }
            //常量参数，初始确定后一般不需更改
            environment {
                REPO_URL = "${map.REPO_URL}"
                //git服务全系统只读账号，无需修改
                CRED_ID = "${map.CRED_ID}"
                //pom.xml的相对路径
                POM_PATH = "${map.POM_PATH}"
                //生成war包的相对路径
                WAR_PATH = "${map.WAR_PATH}"
                //测试人员邮箱地址
                QA_EMAIL = "${map.QA_EMAIL}"
                //接口测试job名称
                ITEST_JOBNAME = "${map.ITEST_JOBNAME}"
            }

            options {
                disableConcurrentBuilds()
                timeout(time: 1, unit: 'HOURS')
                //保持构建的最大个数
                buildDiscarder(logRotator(numToKeepStr: '10'))
            }
            //pipeline的各个阶段场景
            stages {
                stage('代码获取') {
                    steps {
                        //一些初始化操作
                        script {
                            //根据param.server分割获取参数
                            def split = params.server.split(",")
                            serverIP = split[0]
                            jettyPort = split[1]
                            serverName = split[2]
                            serverPasswd = split[3]
                            //场景选择
                            println params.scene
                            //单元测试运行场景
                            isUT = params.scene.contains('scene1:完整流水线') || params.scene.contains('scene2:代码检查')
                            println "isUT=" + isUT
                            //静态代码检查运行场景
                            isCA = params.scene.contains('scene1:完整流水线') || params.scene.contains('scene2:代码检查')
                            println "isCA=" + isCA
                            //部署测试环境运行场景
                            isDP = params.scene.contains('scene1:完整流水线') || params.scene.contains('scene3:测试部署')
                            println "isDP=" + isDP
                            //第三方库安全性检查
                            isDC = params.scene.contains('scene1:完整流水线')
                            println "isDC=" + isDC
                            //接口测试运行场景
                            isIT = params.scene.contains('scene1:完整流水线')
                            println "isIT=" + isIT
                            try {
                                wrap([$class: 'BuildUser']) {
                                    userEmail = "${BUILD_USER_EMAIL},${QA_EMAIL}"
                                    user = "${BUILD_USER_ID}"
                                }
                            } catch (exc) {
                                userEmail = "${QA_EMAIL}"
                                user = "system"
                            }
                            echo "starting fetchCode from ${REPO_URL}......"
                            // Get some code from a GitHub repository
                            git credentialsId: CRED_ID, url: REPO_URL, branch: params.repoBranch
                        }
                    }
                }
            }
        }
    }
}