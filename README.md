# 关于



绝大部份项目的构建流程是：拉取源码---执行单元测试---构建目标包---构建镜像---推送镜像---集群拉取镜像部署 ，此脚本为Jenkins流水线共享库(Jenkins pipline shared library)，将所有步骤统一定义到此脚本中，所有项目引用共享库即可，支持多分支构建，不同的分支发布到不同的环境。在项目的Jenkinsfile中，定义各自项目的参数即可：



```groovy
#!groovy
library 'pipeline-shared-library'
    def map = [:]
    map.put('repoBranch',env.BRANCH_NAME)
    map.put('repoUrl','http://gitlab.balabala.com/development/balabala.git')
    map.put('appName','demo-app')
    map.put('k8sSvcName','kubernetes-service-name')
    map.put('tag','v1.0.1')
    map.put('k8sResourceType','Deployment')
    map.put('runUnitTest','0')
    map.put('credentialsId','3818ec5a-b476-4471-8c47-db36ed4d5eb0')
    map.put('proRegistryAddr','127.0.0.1:8888/registry')
    map.put('fatRegistryAddr','registry.cn-hangzhou.aliyuncs.com/app_k8s')
    map.put('multibrachComposeName','demo-app-multi_master')
    map.put('pubRepoUrl','https://nexus.balabala.com/repository/maven-releases/')
    map.put('pubRepoUrl','https://nexus.balabala.com/repository/maven-snapshots/')
    map.put('gradleConfigFileName','build.gradle')
    if(env.BRANCH_NAME == 'master') {
        map.put('k8sNamespace','pro')
        map.put('multibrachComposeName', 'demo-multi_master')
        map.put('buildJar','demo-service-1.0.0-RELEASE.jar')
    }
    if(env.BRANCH_NAME == 'feature/demo'){
        map.put('k8sNamespace','fat')
        map.put('multibrachComposeName', 'demo-multipipeline_feature_demo')
        map.put('buildJar','demo-service-1.0.0-SNAPSHOT.jar')
    }

ci("gradle",map)
```






































