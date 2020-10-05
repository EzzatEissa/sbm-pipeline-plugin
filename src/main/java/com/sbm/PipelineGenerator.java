package com.sbm;

import com.openshift.restclient.ClientBuilder;
import com.openshift.restclient.IClient;
import com.openshift.restclient.ResourceKind;
import com.openshift.restclient.model.IProject;
import com.openshift.restclient.model.IResource;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mojo(name = "PipelineGenerator", defaultPhase = LifecyclePhase.COMPILE)
public class PipelineGenerator extends AbstractMojo{

    @Parameter(required = true)
    private String projectName;

    @Parameter(required = true)
    private String gitUrl;

    @Parameter(required = true)
    private String clusterUrl;

    @Parameter(required = true)
    private String clusterToken;

    public static final String PROJECT_PIPELINERESOURCE_YML = "/project-pipelineresource.yml";
    public static final String DEPLOYMENT_TASK_YML = "/deployment-task.yml";
    public static final String DEPLOYMENT_PIPELINE_YML = "/deployment-pipeline.yml";
    public static final String DEPLOYMENT_PIPELINE_RUN = "/project-pipelinerun.yml";


    public void execute() throws MojoExecutionException, MojoFailureException {
        try {

            validateInputs();

            IClient client = new ClientBuilder(clusterUrl)
                    .usingToken(clusterToken)
                    .build();


            IResource request = client.getResourceFactory().stub(ResourceKind.PROJECT_REQUEST, projectName);
            IProject project = (IProject) client.create(request);


            String pipelineResourceStr = updateGitUrl(gitUrl);

            IResource pipelineResource = client.getResourceFactory().create(pipelineResourceStr);
            client.create(pipelineResource, projectName);

            IResource taskResource = client.getResourceFactory().create(PipelineGenerator.class.getResourceAsStream(DEPLOYMENT_TASK_YML));
            client.create(taskResource, projectName);

            IResource pipeline = client.getResourceFactory().create(PipelineGenerator.class.getResourceAsStream(DEPLOYMENT_PIPELINE_YML));
            client.create(pipeline, projectName);

            IResource pipelineRun = client.getResourceFactory().create(PipelineGenerator.class.getResourceAsStream(DEPLOYMENT_PIPELINE_RUN));
            client.create(pipelineRun, projectName);

        } catch (Exception ex) {
            throw new MojoExecutionException("Failed to execute plugin", ex);
        }

    }

    private void validateInputs() throws MojoExecutionException, MojoFailureException{

        String patternString = "((git|ssh|http(s)?)|(git@[\\w\\.]+))(:(//)?)([\\w\\.@\\:/\\-~]+)(\\.git)(/)?";
        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(gitUrl);
        boolean isMatched = matcher.matches();
        if(!isMatched)
            throw new MojoExecutionException("git url in not valid url");
    }

    private static String updateGitUrl(String newGitUrl){
        JSONObject resourceJson = null;
        try {
            InputStream inputStream = PipelineGenerator.class.getResourceAsStream(PROJECT_PIPELINERESOURCE_YML);
            StringWriter writer = new StringWriter();
            IOUtils.copy(inputStream, writer, "UTF-8");
            String resourceJsonStr = writer.toString();
            resourceJson = new JSONObject(resourceJsonStr);
            JSONObject spec = (JSONObject) resourceJson.get("spec");
            JSONArray params = (JSONArray) spec.get("params");
            for(int i = 0; i < params.length(); i++){
                JSONObject jsonObj = params.optJSONObject(i );
                if (jsonObj != null && "url".equals(jsonObj.get("name"))){
                    jsonObj.put("value", newGitUrl);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return resourceJson.toString();
    }
}
