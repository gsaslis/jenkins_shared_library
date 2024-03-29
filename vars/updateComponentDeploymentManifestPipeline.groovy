import com.redhat.threescale.productization.ValidatingUtils

def call(Map <String, ?> config = [:]) {

  validateParameters(config)

  def	stash_folder_name = 'deployment_manifests'

  def	image_static_tag = params.image_static_tag ?: config.image_static_tag
  def	upstream_scm_ref = params.upstream_scm_ref ?: config.upstream_scm_ref
  def	downstream_scm_ref = params.downstream_scm_ref ?: config.downstream_scm_ref
  def	manifest_path = params.manifest_path ?: config.manifest_path

  String distribution_scope

  def	manifests_scm_url = params.manifests_scm_url ?: config.manifests_scm_url
  def	manifests_scm_branch = params.manifests_scm_branch ?: config.manifests_scm_branch

  if (!manifests_scm_url)
    throw new Exception("Parameter 'manifests_scm_url' was not provided and is required.")
  if (!manifests_scm_branch)
    throw new Exception("Parameter 'manifests_scm_branch' was not provided and is required.")

  def component = ''

  def ciMessage = params.CI_MESSAGE // UMB message which triggered the build


  pipeline {
    agent {
      node {
        label 'swarm'
      }
    }
    options {
      preserveStashes()
      disableConcurrentBuilds()
    }
    parameters {
      string(
          defaultValue: '',
          description: 'The repository url where the manifests live',
          name: 'manifests_scm_url',
          trim: true
      )
      string(
          defaultValue: '',
          description: 'The repository branch where the manifests live',
          name: 'manifests_scm_branch',
          trim: true
      )
      string(
          name: 'manifest_path',
          defaultValue: '',
          description: 'The path in the manifests repository where the manifest to be updated lives, relative to the repository root, including the manifest file name',
          trim: true
      )
      string(
          name: 'image_static_tag',
          defaultValue: '',
          description: 'The static tag of the container image that should be deployed.',
          trim: true
      )
      string(
          name: 'upstream_scm_ref',
          defaultValue: '',
          description: 'The upstream repo commit SHA from where the productized image originates from.',
          trim: true
      )
      string(
          name: 'downstream_scm_ref',
          defaultValue: '',
          description: 'The downstream repo commit SHA from where the productized image originates from.',
          trim: true
      )
      string(
          defaultValue: '',
          description: 'Contents of the CI message received from Universal Message Bus (UMB).',
          name: 'CI_MESSAGE',
          trim: true
      )
    }
    stages {
      stage("Parse UMB trigger") {
        when {
          expression {
            params.CI_MESSAGE
          }
        }
        agent {
          node {
            label 'swarm'
          }
        }
        steps {
          script {
            echo "Raw message:\n${ciMessage}"

            // Parse the message into a Map
            def ciData = readJSON text: ciMessage
            component = ciData?.component
            image_static_tag = ciData?.image_static_tag
            upstream_scm_ref = ciData?.upstream_scm_ref
            downstream_scm_ref = ciData?.downstream_scm_ref
            distribution_scope = ciData?.distribution_scope
            manifest_path = lookup_manifest_path(distribution_scope, component)

            echo "Image static tag: ${image_static_tag}. \n Manifest path: ${manifest_path}."

          }
        }
      }
      stage('Clone Git Repo') {
        steps {
          script {
            cloneGit(
                k8s_namespace: config.k8s_namespace,
                stash_folder_name: stash_folder_name,
                repository: manifests_scm_url,
                branch: manifests_scm_branch,
                git_user_ssh_key: config.git_user_ssh_key,
                git_user_credentials_id: config.git_user_credentials_id,

            )
          }
        }
      }
      stage('Update Manifest') {
        steps {
          script {
            updateDeploymentManifest(
                manifest_path: manifest_path,
                image_tag: image_static_tag,
                stash_folder_name: stash_folder_name,
                k8s_namespace: config.k8s_namespace,
            )
          }
        }
      }
      stage('Open PR') {
        steps {
          script {
            openPR(
                k8s_namespace: config.k8s_namespace,

                stash_folder_name: stash_folder_name,
                repository: manifests_scm_url,
                branch: manifests_scm_branch,
                file_name: manifest_path,

                git_user_ssh_key: config.git_user_ssh_key,
                git_user_credentials_id: config.git_user_credentials_id,

                //gpg sign
                gpg_passphrase_credentials_id: config.gpg_passphrase_credentials_id,
                gpg_signing_key_id: config.gpg_signing_key_id,
                gpg_signing_key_secret: config.gpg_signing_key_secret,

                //PR
                github_personal_access_token: config.github_personal_access_token,
                commit_message_header: "Deploys image ${image_static_tag} to ${manifest_path}",
                commit_message: "Upstream SHA: ${upstream_scm_ref}",
                commit_message_details: ["Downstream SHA: ${downstream_scm_ref}", "/kind deploy"],
                pr_branch: "deploy/${lookup_deployment_environment(distribution_scope)}/${component}",

            )
          }
        }
      }
      stage('Update result') {
        steps {
          script {
            currentBuild.result = currentBuild.result ?: 'SUCCESS'
          }
        }
      }
    }
  }


}

static def lookup_deployment_environment(String release_stream) {

  Map<String, String> DEPLOYMENT_ENVIRONMENTS = [
    alpha: 'stg',
    candidate: 'stg',
    stable: 'pro',
  ]

  DEPLOYMENT_ENVIRONMENTS[release_stream]
}

static def lookup_manifest_path(String release_stream, String component) {
  def environment = lookup_deployment_environment(release_stream)
  "manifests/${environment}-saas/ocp4/3scale-saas/${component}/${environment}-saas-${component}.yaml"
}


private static void validateParameters(Map<String, ?> config) {
  def validatingUtils = new ValidatingUtils()

  //git author
  validatingUtils.ensureNotEmpty(config, 'git_user_credentials_id')

  //gpg sign
  validatingUtils.ensureNotEmpty(config, 'gpg_passphrase_credentials_id')
  validatingUtils.ensureNotEmpty(config, 'gpg_signing_key_id')
  validatingUtils.ensureNotEmpty(config, 'gpg_signing_key_secret')

  //commit / push
  validatingUtils.ensureNotEmpty(config, 'git_user_ssh_key')

  // Pull Request
  validatingUtils.ensureNotEmpty(config, 'github_personal_access_token')

  // Generic
  validatingUtils.ensureNotEmpty(config, 'k8s_namespace')
}
