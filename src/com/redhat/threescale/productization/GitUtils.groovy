package com.redhat.threescale.productization

/**
 * This needs to run when this is executed in openshift pods, to avoid permissions issues (due to running as non-root containers
 * and openshift running the container with a different user id than the one in the image).
 *
 * @return
 */
def fixNonRootUserIdInContainer(){
	//	This assumes the script exists in the running container, of course. See https://github.com/gsaslis/alpine-git-gpg/blob/main/bin/fixUser.sh for example.
	sh '/usr/bin/fixUser.sh'
}

def checkout(Map <String, ?> config){
	def validatingUtils = new ValidatingUtils()
	validatingUtils.ensureNotEmpty(config, 'git_user_ssh_key')
	validatingUtils.ensureNotEmpty(config, 'branch')
	validatingUtils.ensureNotEmpty(config, 'repository')


	def checkoutExtensions = []
	def unstashed = false

	if (config.repository_name) {

		if (fileExists config.repository_name)
			return

		unstash config.repository_name
		unstashed = fileExists config.repository_name


		checkoutExtensions =
				[[
						 $class           : 'RelativeTargetDirectory',
						 relativeTargetDir: config.repository_name

				 ]]
	}
	if(!unstashed) {

		checkout([
				$class                           : 'GitSCM',
				branches                         : [[name: "*/${config.branch}"]],
				doGenerateSubmoduleConfigurations: false,
				extensions                       : checkoutExtensions,
				submoduleCfg                     : [],
				userRemoteConfigs                : [
						[
								credentialsId: config.git_user_ssh_key,
								url          : config.repository
						]
				]
		])

		stash(
				name: config.repository_name,
				includes: "${config.repository_name}/**",
				useDefaultExcludes: false,
				excludes: '',
		)



	}
}


/**
 * Orchestrates execution of `git config --global user.name` and `git config --global user.emal`,
 * reading values from a Map.
 *
 *
 * @param config It seems strange to have a Map as an argument, I know. Perhaps this is a matter of personal taste,
 * but I prefer to keep it like this so that the code inside the global var is reduced to as little as possible.
 */
def configGitAuthor(Map <String, ?> config) {

	def validatingUtils = new ValidatingUtils()
	validatingUtils.ensureNotEmpty(config, 'github_user_credentials_id')

	unstash 'gitconfig'
	def unstashed = fileExists '/home/git/.gitconfig'
	if(!unstashed) {
		withCredentials([
				usernamePassword(
						credentialsId: config.github_user_credentials_id,
						usernameVariable: 'GIT_USER',
						passwordVariable: 'GIT_EMAIL'
				),
		]) {

			sh 'git config --global user.name "${GIT_USER}"'
			sh 'git config --global user.email "${GIT_EMAIL}"'

			stash(
					name: 'gitconfig',
					includes: "/home/git/.gitconfig",
			)

		}
	}
}

/**
 * Sets up GPG for use with git, so that commits will be signed. Requires a signing key of course.
 *
 *
 * @param config It seems strange to have a Map as an argument, I know. Perhaps this is a matter of personal taste,
 * but I prefer to keep it like this so that the code inside the global var is reduced to as little as possible.
 */

def gpgSetupForGit(Map <String, ?> config) {

	def validatingUtils = new ValidatingUtils()

	validatingUtils.ensureNotEmpty(config, 'gpg_passphrase_credentials_id')
	validatingUtils.ensureNotEmpty(config, 'gpg_signing_key_id')
	validatingUtils.ensureNotEmpty(config, 'gpg_signing_key_secret')


	withCredentials([
			string(credentialsId: config.gpg_passphrase_credentials_id, variable: 'GPG_PASSPHRASE'),
			file(credentialsId: config.gpg_signing_key_secret, variable: 'GPG_KEY'),
			string(credentialsId: config.gpg_signing_key_id, variable: 'GPG_SIGNING_KEY_ID'),
	]) {

		sh """
      printf "\${GPG_PASSPHRASE}" > \${WORKSPACE}/gpg-passphrase
      printf "#!/bin/sh\n/usr/bin/gpg --batch --no-tty --pinentry-mode loopback --passphrase-file \${WORKSPACE}/gpg-passphrase \"\\\$@\"\n" > \${WORKSPACE}/gpg-with-passphrase
      cat \${WORKSPACE}/gpg-with-passphrase
      chmod +x \${WORKSPACE}/gpg-with-passphrase
      git config --global gpg.program \${WORKSPACE}/gpg-with-passphrase
    """

		sh "\${WORKSPACE}/gpg-with-passphrase --import \${GPG_KEY}"

		sh "git config --global user.signingkey \${GPG_SIGNING_KEY_ID}"

		sh 'git config --list'
	}
}

return this