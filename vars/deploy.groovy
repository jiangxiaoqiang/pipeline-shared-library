def call(Map config) {
    node ('jnlp-slave'){
        //阿里云私服配置
        def registry_url = "registry-vpc.cn-beijing.aliyuncs.com"
        def registry_auth = "docker-registry-aliyun"
        def image_name = "${registry_url}/project/:${config.app_name}_${env.BUILD_ID}"
        //
        stage('Git Clone') {
            checkout([$class: 'GitSCM', branches: [[name: '*/test']], doGenerateSubmoduleConfigurations: false,userRemoteConfigs: [[credentialsId: 'git账号ID', url: config.git_url]]])
        }
        stage('Maven build'){
            sh "mvn  -DskipTests clean package -U  -P test -f ${config.pom_path}"
        }
        stage("Docker build"){
            sh """
            /bin/cp /data/k8s-file/Dockerfile_template Dockerfile
            sed  -i s#JAR_FILE=.*#JAR_FILE=${config.jar_file}# Dockerfile
            """
            docker.withRegistry("https://" + registry_url,registry_auth) {
                def customImage = docker.build(image_name)
                customImage.push()
            }
        }
        stage("k8s deploy") {
            kubernetesDeploy configs: 'Deployment.yml', kubeconfigId: "K8s凭据id",textCredentials: [serverUrl: 'https://']
        }
    }
}