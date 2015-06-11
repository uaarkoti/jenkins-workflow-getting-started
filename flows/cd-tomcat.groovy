// Global Libraries
tomcat = new com.cb.web.Tomcat(hostname: "localhost", port: "8180", adminUser: "admin", adminPassword: "tomcat")
util = new com.cb.util.BasicUtilities()

// Local variables
artifactName = 'webapp.war'
artifact = "target/${artifactName}"

// Closures to be executed by tomcat libraries
deployClosure = {war, url, id -> sh "curl --upload-file ${war} '${url}?path=/${id}&update=true'"}
undeployClosure = {url, id -> sh "curl '${url}?path=/${id}'"}
deployClosure.resolveStrategy = Closure.DELEGATE_FIRST
undeployClosure.resolveStrategy = Closure.DELEGATE_FIRST

node('master') {
   git url: 'https://github.com/jenkinsbyexample/workflow-plugin-pipeline-demo.git'
   devQAStaging()
}

production()

def devQAStaging() {

    stage 'Build'
    sh 'mvn clean package'
    archive artifact

    stage 'Code Coverage'
    echo 'Using Sonar for code coverage'

    stage 'QA'

    parallel(longerTests: {
        runWithServer {url ->
            sh "mvn -f sometests/pom.xml test -Durl=${url} -Dduration=10"
        }
    }, quickerTests: {
        runWithServer {url ->
            sh "mvn -f sometests/pom.xml test -Durl=${url} -Dduration=5"
        }
    })

    try {
        checkpoint('Before Staging')
    } catch (NoSuchMethodError _) {
        echo 'Checkpoint feature available in Jenkins Enterprise by CloudBees.'
    }

    stage name: 'Staging', concurrency: 1
    tomcat.deploy(artifact, 'staging', deployClosure)
}

def production() {
    input message: "Does ${tomcat.hostUrl}/staging/ look good?"
    try {
        checkpoint('Before production')
    } catch (NoSuchMethodError _) {
        echo 'Checkpoint feature available in Jenkins Enterprise by CloudBees.'
    }

    stage name: 'Production', concurrency: 1
    node('master') {
        sh "curl -I ${tomcat.hostUrl}/staging/"
        unarchive mapping: ['target/webapp.war' : 'webapp.war']
        tomcat.deploy(artifactName, 'production', deployClosure)
        echo "Deployed to ${tomcat.hostUrl}/production/"
    }
}

def runWithServer(body) {
    def id = util.random()
    tomcat.deploy(artifact, id, deployClosure)
    try {
        body.call "${tomcat.hostUrl}/${id}/"
    } finally {
        tomcat.undeploy(id, undeployClosure)
    }
}
