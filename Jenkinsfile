pipeline {
  agent any // контроллер выполняет сборку сам; в проде так не делают — ставят агентов

  stages {
    stage('build') {
      steps {
        // аналог working-directory в GHA
        dir('02-gradle') {
          sh './gradlew --no-daemon build'
        }
      }
    }
  }

  post {
    always {
      // тот же артефакт, что upload-artifact в GHA и artifacts: в GitLab
      archiveArtifacts artifacts: '02-gradle/build/libs/*.jar', allowEmptyArchive: true
    }
  }
}
