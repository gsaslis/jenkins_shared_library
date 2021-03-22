#!/usr/bin/env groovy

def git_repository = "git@github.com:gsaslis/testprojectrepo.git"
def git_branch = "main"
def manifest_path = "manifests/staging/ocp4/system/system.yaml"
def image_static_tag_to_deploy = "some_tag1"


def k8s_namespace = 'jenkins-csb-3scaleprod'

// GitHub User details

/**
 * Prefer the old PEM format to avoid compatibility issues with BouncyCastle. Generate your key with `sh-keygen -m PEM -t rsa -C "3scale-ninjabot" -b 8192`
 */
def git_user_ssh_key = 'github-3scale-ninjabot-ssh-private-key'
def git_user_credentials_id = 'github_3scale_ninjabot_username_email'
def github_personal_access_token = "github-ninjabot-personal-access-token"

// GPG signing
def gpg_passphrase_credentials_id = 'github_3scale_ninjabot_gpg_passphrase'
def gpg_signing_key_id = 'github_3scale_ninjabot_gpg_signing_key_id'
def gpg_signing_key_secret = 'github_3scale_ninjabot_gpg_signing_key_secret'

//////////////////////////// You probably only need to change stuff ABOVE this line /////////////////////////////////////

loadLibrary()

// use global var that creates a reusable declarative pipeline definition
updateComponentDeploymentManifestPipeline (

    git_user_ssh_key: git_user_ssh_key,
    git_user_credentials_id: git_user_credentials_id,
    github_personal_access_token: github_personal_access_token,

    gpg_passphrase_credentials_id: gpg_passphrase_credentials_id,
    gpg_signing_key_id: gpg_signing_key_id,
    gpg_signing_key_secret: gpg_signing_key_secret,

    manifests_scm_url: git_repository,
    manifests_scm_branch: git_branch,

    manifest_path: manifest_path,
    image_static_tag: image_static_tag_to_deploy,

    k8s_namespace: k8s_namespace,
    slack_channel: 'some_slack_channel',
    notifications_email: 'some@email.com',
)




def loadLibrary() {

  def libSCM = modernSCM(
      [
          $class: 'GitSCMSource',
          remote: 'https://github.com/gsaslis/jenkins_shared_library.git',
      ]
  )

    library identifier: "3scale-shared-library_branch@main",
        retriever: libSCM
}
