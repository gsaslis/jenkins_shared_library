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

            // For SSH private key authentication, try the sshagent step from the SSH Agent plugin.
            sshagent(credentials: [config.git_user_ssh_key]) {

              gitUtils.fixNonRootUserIdInContainer()

              gitUtils.configGitAuthor(config)

              gitUtils.checkout(config)

            }
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

}


