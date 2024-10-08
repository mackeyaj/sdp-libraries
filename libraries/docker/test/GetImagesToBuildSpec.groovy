/*
  Copyright © 2018 Booz Allen Hamilton. All Rights Reserved.
  This software package is licensed under the Booz Allen Public License. The license can be found in the License file or at http://boozallen.github.io/licenses/bapl
*/

package libraries.docker

public class GetImagesToBuildSpec extends JTEPipelineSpecification {

  def GetImagesToBuild = null

  public static class DummyException extends RuntimeException {
    public DummyException(String _message) { super( _message ); }
  }

  def setup() {
    GetImagesToBuild = loadPipelineScriptForStep("docker", "get_images_to_build")
    explicitlyMockPipelineStep("get_registry_info")

    getPipelineMock("get_registry_info")() >> ["test_registry", "test_cred"]
  }

  def "Get_registry_info method is called" () {
    setup:
      GetImagesToBuild.getBinding().setVariable("config", [:])
    when:
      GetImagesToBuild()
    then:
      1 * getPipelineMock("get_registry_info")() >> ["test_registry", "test_cred"]
  }

  def "path_prefix Is An Empty String If No repo_path_prefix Is Set In The Config" () {
    setup:
      GetImagesToBuild.getBinding().setVariable("config", [repo_path_prefix: null, build_strategy: build_strategy])
      GetImagesToBuild.getBinding().setVariable("env", [REPO_NAME: "git_repo", GIT_SHA: "8675309"])
      getPipelineMock("findFiles")([glob: "*/Dockerfile"]) >> [[path: "service/Dockerfile"]]
    when:
      def imageList = GetImagesToBuild()
    then:
      imageList == [[registry: "test_registry", repo:repo , context: build_context, tag: "8675309"]]
    where:
      build_strategy | build_context | repo
      "dockerfile"   | "."           | "git_repo"
      "modules"      | "service"     | "git_repo_service"
  }

  def "path_prefix Is Properly Prepended To Repo Value" () {
    setup:
      GetImagesToBuild.getBinding().setVariable("config", [repo_path_prefix: "test_prefix", build_strategy: build_strategy])
      GetImagesToBuild.getBinding().setVariable("env", [REPO_NAME: "git_repo", GIT_SHA: "8675309"])
      getPipelineMock("findFiles")([glob: "*/Dockerfile"]) >> [[path: "service/Dockerfile"]]
    when:
      def imageList = GetImagesToBuild()
    then:
      imageList == [[registry: "test_registry", repo: repo, context: build_context, tag: "8675309"]]
    where:
      build_strategy | build_context | repo
      "dockerfile"   | "."           | "test_prefix/git_repo"
      "modules"      | "service"     | "test_prefix/git_repo_service"
  }

  def "image_name Properly Overrides env.REPO_NAME When Set" () {
    setup:
      GetImagesToBuild.getBinding().setVariable("config", [repo_path_prefix: "test_prefix", build_strategy: build_strategy, image_name: "my-cool-image-name"])
      GetImagesToBuild.getBinding().setVariable("env", [REPO_NAME: "git_repo", GIT_SHA: "8675309"])
      getPipelineMock("findFiles")([glob: "*/Dockerfile"]) >> [[path: "service/Dockerfile"]]
    when:
      def imageList = GetImagesToBuild()
    then:
      imageList == [[registry: "test_registry", repo: repo, context: build_context, tag: "8675309"]]
    where:
      build_strategy | build_context | repo
      "dockerfile"   | "."           | "test_prefix/my-cool-image-name"
      "modules"      | "service"     | "test_prefix/my-cool-image-name_service"
  }

  def "Invalid build_strategy Throws Error" () {
    setup:
      GetImagesToBuild.getBinding().setVariable("config", [build_strategy: x])
    when:
      GetImagesToBuild()
    then:
      y * getPipelineMock("error")("build strategy: ${x} not one of [docker-compose, modules, dockerfile, buildx]")
    where:
      x                | y
      "docker-compose" | 0
      "Kobayashi Maru" | 1
      "modules"        | 0
      "dockerfile"     | 0
      "Starfleet"      | 1
      "buildx"         | 0
  }

  def "docker-compose build_strategy Throws Error" () {
    setup:
      GetImagesToBuild.getBinding().setVariable("config", [build_strategy: "docker-compose"])
    when:
      GetImagesToBuild()
    then:
      1 * getPipelineMock("error")("docker-compose build strategy not implemented yet")
  }

  def "modules build_strategy Builds Correct Image List" () {
    setup:
      GetImagesToBuild.getBinding().setVariable("config", [build_strategy: "modules"])
      GetImagesToBuild.getBinding().setVariable("env", [REPO_NAME: "Vulcan", GIT_SHA: "1234abcd"])
      getPipelineMock("findFiles")([glob: "*/Dockerfile"]) >> [[path: "planet/Romulus"], [path: "planet2/Earth"]]
      GetImagesToBuild.getBinding().setVariable("pipelineConfig", [application_image_repository: "Enterprise"])
    when:
      def imageList = GetImagesToBuild()
    then:
      imageList == [
        [
          registry: "test_registry",
          repo: "vulcan_planet",
          context: "planet",
          tag: "1234abcd"
        ], [
          registry: "test_registry",
          repo: "vulcan_planet2",
          context: "planet2",
          tag: "1234abcd"
        ]
      ]
  }
  def "dockerfile build_strategy Builds Correct Image List" () {
    setup:
      GetImagesToBuild.getBinding().setVariable("config", [build_strategy: "dockerfile"])
      GetImagesToBuild.getBinding().setVariable("env", [REPO_NAME: "Vulcan", GIT_SHA: "5678efgh"])
      getPipelineMock("findFiles")([glob: "*/Dockerfile"]) >> [[path: "planet/Romulus"]]
      GetImagesToBuild.getBinding().setVariable("pipelineConfig", [application_image_repository: "Enterprise"])
    when:
      def imageList = GetImagesToBuild()
    then:
      imageList == [[registry: "test_registry", repo: "vulcan", context: ".", tag: "5678efgh"]]
  }

  def "buildx build_strategy Builds Correct Image for single multiarch image" () {
    setup:
      GetImagesToBuild.getBinding().setVariable("config", [build_strategy: "buildx", 
        repo_path_prefix: "image", 
        buildx: [image1: [platforms: ["linux/amd64", "linux/arm64", "linux/arm/v7"], build_args: [BASE_IMAGE: "image"], useLatestTag: true]]])
      GetImagesToBuild.getBinding().setVariable("env", [REPO_NAME: "Vulcan", GIT_SHA: "5678efgh"])
    when:
      def imageList = GetImagesToBuild()
    then:
      imageList == [
        [
          registry: "test_registry",
          repo: "image/vulcan",
          tag: "5678efgh",
          context: ".",
          dockerfilePath: "",
          build_args: [BASE_IMAGE: "image"],
          platforms: ["linux/amd64", "linux/arm64", "linux/arm/v7"],
          useLatestTag: true
        ]]
  }
    def "buildx build_strategy Builds Correct Image for single multiarch image with multiple tags" () {
    setup:
      GetImagesToBuild.getBinding().setVariable("config", [build_strategy: "buildx", 
        repo_path_prefix: "image", same_repo_different_tags: true,
        buildx: [image1: [platforms: ["linux/arm/v7"], tag: "tag", useLatestTag: true],
                 image2: [platforms: ["linux/amd64"], tag: "tag", useLatestTag: false]]])
      GetImagesToBuild.getBinding().setVariable("env", [REPO_NAME: "Vulcan", GIT_SHA: "5678efgh"])
    when:
      def imageList = GetImagesToBuild()
    then:
      imageList == [
        [
          registry: "test_registry",
          repo: "image/vulcan",
          tag: "tag-image1",
          context: ".",
          dockerfilePath: "",
          build_args: null,
          platforms: ["linux/arm/v7"],
          useLatestTag: true
        ],
        [
          registry: "test_registry",
          repo: "image/vulcan",
          tag: "tag-image2",
          context: ".",
          dockerfilePath: "",
          build_args: null,
          platforms: ["linux/amd64"],
          useLatestTag: false
        ]]
  }
    def "buildx build_strategy Builds Correct Image for multiple multiarch images" () {
    setup:
      GetImagesToBuild.getBinding().setVariable("config", [build_strategy: "buildx", 
        repo_path_prefix: "image", 
        buildx: [image1: [platforms: ["linux/amd64", "linux/arm64", "linux/arm/v7"], context: "context1", dockerfile_path: "Dockerfile.test", useLatestTag: true],
                 image2: [platforms: ["linux/amd64", "linux/arm64", "linux/arm/v7"], context: "context2", useLatestTag: true]]])
      GetImagesToBuild.getBinding().setVariable("env", [REPO_NAME: "Vulcan", GIT_SHA: "5678efgh"])
    when:
      def imageList = GetImagesToBuild()
    then:
      imageList == [
        [
          registry: "test_registry",
          repo: "image/vulcan_image1",
          tag: "5678efgh",
          context: "context1",
          dockerfilePath: " -f Dockerfile.test",
          build_args: null,
          platforms: ["linux/amd64", "linux/arm64", "linux/arm/v7"],
          useLatestTag: true
        ],
        [
          registry: "test_registry",
          repo: "image/vulcan_image2",
          tag: "5678efgh",
          context: "context2",
          dockerfilePath: "",
          build_args: null,
          platforms: ["linux/amd64", "linux/arm64", "linux/arm/v7"],
          useLatestTag: true
        ]]
  }
}

