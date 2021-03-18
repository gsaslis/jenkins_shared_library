package com.redhat.threescale.productization

def ensureNotEmpty(Map<String, ?> config, String key){
  if (!config[key]){
    throw new Exception("Parameter '${key}' was not provided and is required.")
  }
}

def ensureYesNo(Map<String, ?> config, String key){
  if(!(config[key] ==~ /(?i)(yes|no)/ )){
    print "${key}: ${config[key]}"
    throw new Exception("Parameter '${key}' needs to be 'yes' or 'no'.")
  }
}

def ensureSupportedComponent(String component){
  def components = [
      'apicast',
      'backend',
      'istio-adapter',
      'memcached',
      'system',
      'toolbox',
      'zync',
      '3scale-operator',
      '3scale-operator-metadata',
      '3scale-operator-bundle',
      'apicast-operator',
      'apicast-operator-metadata',
      'apicast-operator-bundle',
  ]

 	// No 3scale component specified, we need to exit
 	if (!component) {
 		throw new Exception("Parameter 'component' was not provided - please specify the 3scale component to build")
 	}

 	// Check if it is a supported component
 	if (!components.contains(component)) {
 		throw new Exception("Unsupported component '${component}' specified - please specify a valid 3scale component to build")
 	}

}

return this
