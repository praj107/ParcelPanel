import jenkins.model.Jenkins
import hudson.plugins.git.BranchSpec
import hudson.plugins.git.GitSCM
import hudson.plugins.git.UserRemoteConfig
import hudson.model.BooleanParameterDefinition
import hudson.model.ChoiceParameterDefinition
import hudson.model.ParametersDefinitionProperty
import hudson.model.StringParameterDefinition
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob

def repoUrl = System.getenv('PARCELPANEL_REPO_URL') ?: 'https://github.com/praj107/ParcelPanel.git'
def branchSpec = '*/main'
def gitCredentialsId = System.getenv('PARCELPANEL_GIT_CREDENTIAL_ID') ?: 'parcelpanel-git-http'

def jobDefinitions = [
    [
        name: 'ParcelPanel-CI',
        scriptPath: 'Jenkinsfile',
        description: 'Continuous verification for unit tests, lint, and debug assembly.',
        parameters: [
            [type: 'boolean', name: 'RUN_LINT', defaultValue: true, description: 'Run Android lint as part of CI validation.'],
            [type: 'string', name: 'ANDROID_SDK_ROOT_OVERRIDE', defaultValue: '', description: 'Optional SDK path override.']
        ]
    ],
    [
        name: 'ParcelPanel-Connected',
        scriptPath: 'ci/jenkins/pipelines/connected.Jenkinsfile',
        description: 'Emulator or device validation pipeline for connected tests.',
        parameters: [
            [type: 'boolean', name: 'RUN_UNIT_TESTS', defaultValue: true, description: 'Run JVM unit tests before connected validation.'],
            [type: 'string', name: 'ANDROID_SDK_ROOT_OVERRIDE', defaultValue: '', description: 'Optional SDK path override.']
        ]
    ],
    [
        name: 'ParcelPanel-Release',
        scriptPath: 'ci/jenkins/pipelines/release.Jenkinsfile',
        description: 'Release pipeline for signed assets and GitHub release publication.',
        parameters: [
            [type: 'choice', name: 'RELEASE_TYPE', choices: ['patch', 'minor', 'major', 'chore'], description: 'Version bump policy before release packaging.'],
            [type: 'boolean', name: 'RUN_LINT', defaultValue: true, description: 'Run Android lint in release validation.'],
            [type: 'boolean', name: 'RUN_CONNECTED_TESTS', defaultValue: false, description: 'Run connected Android tests when infrastructure is ready.'],
            [type: 'boolean', name: 'FORCE_RELEASE_TAG_UPDATE', defaultValue: false, description: 'Allow force-updating an existing release tag when repairing a partial release.'],
            [type: 'string', name: 'ANDROID_SDK_ROOT_OVERRIDE', defaultValue: '', description: 'Optional SDK path override.']
        ]
    ]
]

def buildScm = {
    new GitSCM(
        [new UserRemoteConfig(repoUrl, null, null, gitCredentialsId)],
        [new BranchSpec(branchSpec)],
        false,
        [],
        null,
        null,
        []
    )
}

def buildParameters = { definitions ->
    definitions.collect { definition ->
        switch (definition.type) {
            case 'boolean':
                return new BooleanParameterDefinition(definition.name, definition.defaultValue, definition.description)
            case 'string':
                return new StringParameterDefinition(definition.name, definition.defaultValue, definition.description)
            case 'choice':
                return new ChoiceParameterDefinition(definition.name, definition.choices.join('\n'), definition.description)
            default:
                throw new IllegalArgumentException("Unsupported parameter type: ${definition.type}")
        }
    }
}

def jenkins = Jenkins.instance
jobDefinitions.each { definition ->
    WorkflowJob job = jenkins.getItemByFullName(definition.name, WorkflowJob)
    if (job == null) {
        job = jenkins.createProject(WorkflowJob, definition.name)
        println "created:${definition.name}"
    } else {
        println "updated:${definition.name}"
    }

    def flowDefinition = new CpsScmFlowDefinition(buildScm(), definition.scriptPath)
    flowDefinition.setLightweight(true)
    job.setDefinition(flowDefinition)
    job.setDescription(definition.description)
    job.setDisabled(false)
    job.removeProperty(ParametersDefinitionProperty)
    job.addProperty(new ParametersDefinitionProperty(buildParameters(definition.parameters)))
    job.save()
}

println 'bootstrap-complete'
