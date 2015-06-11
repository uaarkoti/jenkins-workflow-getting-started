// Global Libraries

// Tomcat library to deploy / undeploy to tomcat
tomcat = new com.cb.web.Tomcat(hostname: "localhost", port: "8180", adminUser: "admin", adminPassword: "tomcat")

// Simple utility
util = new com.cb.util.BasicUtilities()

// Local variables
artifactName = 'webapp.war'
artifact = "target/${artifactName}"

// Closures to be executed by tomcat library to deploy/undeploy
deployClosure = {war, url, id -> sh "curl --upload-file ${war} '${url}?path=/${id}&update=true'"}
undeployClosure = {url, id -> sh "curl '${url}?path=/${id}'"}
deployClosure.resolveStrategy = Closure.DELEGATE_FIRST
undeployClosure.resolveStrategy = Closure.DELEGATE_FIRST

// Execute the following steps on the master
node('master') {
   git url: 'https://github.com/jenkinsbyexample/workflow-plugin-pipeline-demo.git'
   devQAStaging()
}

production()

def devQAStaging() {

    // Execute maven build and archive artifacts
    stage 'Build'
    sh 'mvn clean package'
    archive artifact

    // TODO : Setup code coverage
    stage 'Code Coverage'
    echo 'Using Sonar for code coverage'

    // Run tests in parallel and publish report
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
 
    step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/TEST-*.xml'])

    // Assuming the tests above take a while (which is probably true in real world)
    // Setup a checkpoint so that if the build fails after the checkpoint, its possible
    // to restart the build from the checkpoint
    // NOTE: The try/catch block makes sure the build does not fail if checkpoint functionality
    // doesn't exist (because its available through CloudBees Jenkins Enterprise)
    try {
        checkpoint('Before Staging')
    } catch (NoSuchMethodError _) {
        echo 'Checkpoint feature available in Jenkins Enterprise by CloudBees.'
    }

    // Make sure only one build can enter this stage
    stage name: 'Staging', concurrency: 1

    // Deploy the artifact to Tomcat
    tomcat.deploy(artifact, 'staging', deployClosure)
}

def production() {

    // Wait for someone to validate that the deployment looks good.
    // Its also possible to add security here to make sure only people
    // With a specific role can resume the build from here
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

// Simple utility function to run tests on tomcat at random url
def runWithServer(body) {
    def id = util.random()
    tomcat.deploy(artifact, id, deployClosure)
    try {
        body.call "${tomcat.hostUrl}/${id}/"
    } finally {
        tomcat.undeploy(id, undeployClosure)
    }
}
