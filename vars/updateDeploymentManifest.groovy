import com.redhat.threescale.productization.ValidatingUtils

def call(Map <String, ?> config = [:]) {

  validateConfig(config)

  try {

    def changed = false
    def downstream_scm_ref = ''

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
                  name: 'yq',
                  image: 'quay.io/gsaslis/mikefarah_yq:3',
                  command: 'cat',
                  ttyEnabled: true,
              ),
          ]
      ) {
        node(POD_LABEL) {
          container('yq') {

            unstash config.stash_folder_name

            dir(config.stash_folder_name) {

              def old_image_tag = sh returnStdout: true, script: "yq read ${config.manifest_path} 'spec.image.tag'"

              changed = (old_image_tag != config.image_tag)


              if (changed) {
                sh """
                  echo 'Old ${config.manifest_path} content:'
                  cat ${config.manifest_path}
  
                  yq write --inplace ${config.manifest_path} 'spec.image.tag' ${config.image_tag}

                  echo 'New ${config.manifest_path} content:'
                  cat ${config.manifest_path}
                """

              }

            }
            stash(
                name: config.stash_folder_name,
                includes: "${config.stash_folder_name}/**",
                useDefaultExcludes: false,
                excludes: '',
            )

          }
        }
      }
    }

  } catch (e) {
    echo "Updating deployment manifest failed: ${e}"
    currentBuild.result = 'FAILURE'
    throw e
  }
}

private static void validateConfig(Map<String, ?> config) {

  def validatingUtils = new ValidatingUtils()

  validatingUtils.ensureNotEmpty(config, 'k8s_namespace')
  validatingUtils.ensureNotEmpty(config, 'manifest_path')
  validatingUtils.ensureNotEmpty(config, 'image_tag')
  validatingUtils.ensureNotEmpty(config, 'stash_folder_name')

}
