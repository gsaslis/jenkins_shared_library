package com.redhat.threescale.productization

import com.homeaway.devtools.jenkins.testing.JenkinsPipelineSpecification
import spock.lang.Unroll

class ValidatingUtilsSpec extends JenkinsPipelineSpecification {
  def validatingUtils = null

  def setup() {
    validatingUtils = loadPipelineScriptForTest("../../src/com/redhat/threescale/productization/ValidatingUtils.groovy")
    validatingUtils.getBinding().setVariable("env", [:])

    explicitlyMockPipelineVariable("out")
  }


  def "[ensureNotEmpty] will NOT throw error if non-null" () {
    given: "some map with a key with non-empty value"
      Map<String, ?> config = ['existing_key': 'some_value']

    when: "we check if the value is not empty"
      validatingUtils.ensureNotEmpty(config, 'existing_key')

    then: "no exception is thrown"
      notThrown(Exception)
  }

  def "[ensureNotEmpty] will throw error if is null" () {
    given: "an empty map"
      Map<String, ?> config = [:]

    and: "a non-existing key"
      def key = 'non_existing_key'

    when: "we check if the value is not empty"
      validatingUtils.ensureNotEmpty(config, key)

    then: "an exception is thrown with the below error message"
      Exception e = thrown()
      e.getMessage() == "Parameter '${key}' was not provided and is required."
  }

  def "[ensureSupportedComponent] will throw error if component is null" () {
    given: "a null component"
      def component = null

    when: "we check if it is a supported component"
      validatingUtils.ensureSupportedComponent(component)

    then: "an exception is thrown with the below error message"
      Exception e = thrown()
      e.getMessage() == "Parameter 'component' was not provided - please specify the 3scale component to build"
  }

  def "[ensureSupportedComponent] will throw error if component is not in list of supported components" () {
    given: "some non-supported component"
      def component = '42'

    when:
      validatingUtils.ensureSupportedComponent(component)

    then: "an exception is thrown with the below error message"
      Exception e = thrown()
      e.getMessage() == "Unsupported component '${component}' specified - please specify a valid 3scale component to build"
  }

  def "[ensureYesNo] will throw error if 'key' is null" () {

    given: "an empty map (where any key has null value)"
      Map<String, ?> config = [:]

    when: "we validate whether key value is 'yes' or 'no'"
      validatingUtils.ensureYesNo(config, 'key')

    then: "an exception is thrown with the below error message"
      Exception e = thrown()
      e.getMessage() == "Parameter 'key' needs to be 'yes' or 'no'."

  }

  @Unroll
  def "[ensureYesNo] will throw error because 'key' value #value is invalid" () {
    given: "a map with an invalid value for 'key' "
      Map<String, ?> config = ['key': value]

    when: "we validate whether key value is 'yes' or 'no'"
      validatingUtils.ensureYesNo(config, 'key')

    then: "an exception is thrown with the below error message"
      Exception e = thrown()
      e.getMessage() == "Parameter 'key' needs to be 'yes' or 'no'."

    where: "some invalid values are below"
      value << [null, '', '42', 'abcdef']
  }

  @Unroll
  def "[ensureYesNo] will NOT throw error because 'key' value #value is valid" () {
    given: "a map with a valid key-value pair"
      Map<String, ?> config = ['key': value]

    when: "we validate whether key value is 'yes' or 'no'"
        validatingUtils.ensureYesNo(config, 'key')

    then: "no exception is thrown"
      notThrown(Exception)

    where: "valid values are the below"
      value << ['yes', 'no', 'YES', 'NO']
  }

  @Unroll
  def "[validImageName] accepts only valid image URLs"() {

    expect: "that a true/false return value depending on whether image is valid"
      assert validatingUtils.validImageName(imageName) == returnValue

    where: "some scenarios are below"
      imageName                                     || returnValue
      'registry.redhat.io/3scale-amp25/zync:latest' || false
      null                                          || false
      ''                                            || false
      'registry-proxy.engineering.redhat.com/3scale-amp25/zync:3scale-amp-2.5-rhel-7-containers-candidate-24389-20190502114201' || true

  }



}
