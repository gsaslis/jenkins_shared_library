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

            gitUtils.fixNonRootUserIdInContainer()

            gitUtils.configGitAuthor(config)

            gitUtils.clone(config)

            gitUtils.gpgSetupForGit(config)

            gitUtils.commitAndPush(config)

            gitUtils.openPR(config)

          }
        }
      }

    }
  }


}

private static void validateConfig(Map<String, ?> config) {

  def validatingUtils = new ValidatingUtils()
  validatingUtils.ensureNotEmpty(config, 'k8s_namespace')

  //checkout
  validatingUtils.ensureNotEmpty(config, 'git_user_ssh_key')
 	validatingUtils.ensureNotEmpty(config, 'branch')
 	validatingUtils.ensureNotEmpty(config, 'repository')

  //git author
  validatingUtils.ensureNotEmpty(config, 'git_user_credentials_id')

  //gpg sign
  validatingUtils.ensureNotEmpty(config, 'gpg_passphrase_credentials_id')
 	validatingUtils.ensureNotEmpty(config, 'gpg_signing_key_id')
 	validatingUtils.ensureNotEmpty(config, 'gpg_signing_key_secret')

  //commit / push
  validatingUtils.ensureNotEmpty(config, 'file_name')
  validatingUtils.ensureNotEmpty(config, 'commit_message')

  // Pull Request
  validatingUtils.ensureNotEmpty(config, 'github_personal_access_token')
  validatingUtils.ensureNotEmpty(config, 'pr_branch')

}




