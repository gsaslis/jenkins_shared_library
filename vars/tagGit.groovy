import com.redhat.threescale.productization.ValidatingUtils
import com.redhat.threescale.productization.GitUtils

def call(Map <String, ?> config = [:]) {

  validateConfig(config)

  def gitUtils = new GitUtils()

  script {
    withEnv(["PATH+OC=${tool 'oc3.11'}"]) {
      podTemplate(
          cloud: 'openshift',
          containers: [
              containerTemplate(
                  name: 'jnlp',
                  image: "image-registry.openshift-image-registry.svc:5000/${config.k8s_namespace}/jenkins-contra-slave:stable",
                  workingDir: '/home/jenkins/agent',
                  command: '',
                  args: '${computer.jnlpmac} ${computer.name}',
                  ttyEnabled: false,
              ),
              containerTemplate(
                  name: 'git',
                  image: 'quay.io/gsaslis/alpine-git-gpg:latest',
                  alwaysPullImage: true,
                  command: 'cat',
                  ttyEnabled: true,
              ),
          ]
      ) {
        node(POD_LABEL) {
          container('git') {

            if(config.ssh_key_type){
              sh "mkdir -p ~/.ssh/"
              sh "ssh-keyscan -t ${config.ssh_key_type} ${config.git_server} >> ~/.ssh/known_hosts"
            }
            
            gitUtils.fixNonRootUserIdInContainer()

            gitUtils.configGitAuthor(config)

            gitUtils.clone(config)

            gitUtils.gpgSetupForGit(config)

            tagAndPush(config)
          }
        }
      }

    }
  }


}

private static void validateConfig(Map<String, ?> config) {

  def validatingUtils = new ValidatingUtils()
  validatingUtils.ensureNotEmpty(config, 'k8s_namespace')
  validatingUtils.ensureNotEmpty(config, 'git_user_ssh_key')
  validatingUtils.ensureNotEmpty(config, 'major_version')
  validatingUtils.ensureNotEmpty(config, 'minor_version')
  validatingUtils.ensureNotEmpty(config, 'patch_version')
  validatingUtils.ensureNotEmpty(config, 'release_qualifier')
}




static String prefix(String release_qualifier){
  switch(release_qualifier) {
  //There is case statement defined for 4 cases
  // Each case statement section has a break condition to exit the loop

    case ~/^ER.*$/:
      return "Engineering Release - ${release_qualifier}"
    case ~/^CR.*$/:
      return "Candidate Release - ${release_qualifier}"
    case 'GA':
      return 'General Availability Release'
    default:
      return 'Release'
  }
}



def tagAndPush(Map <String, ?> config){

  withCredentials([
      sshUserPrivateKey(
          credentialsId: config.git_user_ssh_key,
          keyFileVariable: 'PRIVATE_SSH_KEY',
          passphraseVariable: 'PASSPHRASE',
          usernameVariable: 'SSH_USERNAME'
      )
  ]) {

    dir(config.stash_folder_name) {

      def tag = "3scale-${config.major_version}.${config.minor_version}.${config.patch_version}-${config.release_qualifier}"
      def tag_message = "${prefix(config.release_qualifier as String)} of 3scale ${config.major_version}.${config.minor_version}.${config.patch_version}"

      sh "git tag --sign --message='${tag_message}' ${tag}"
      sh """
      export GIT_SSH_COMMAND="ssh -oStrictHostKeyChecking=no -i $PRIVATE_SSH_KEY"
      git push origin ${tag}
    """
    }
  }
}

