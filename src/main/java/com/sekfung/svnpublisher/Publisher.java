package com.sekfung.svnpublisher;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.common.collect.Lists;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.tmatesoft.svn.core.SVNException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author sekfung
 * <P</P>
 */
public class Publisher extends Recorder implements SimpleBuildStep {

    private String svnUrl;
    private String credentialsId;
    private String commitMessage;
    private String strategy;
    private List<ImportItem> artifacts = Lists.newArrayList();

    @DataBoundConstructor
    public Publisher(String svnUrl, String credentialsId, String commitMessage, String strategy, List<ImportItem> artifacts) {
        this.svnUrl = svnUrl;
        this.credentialsId = credentialsId;
        this.artifacts = artifacts;
        this.commitMessage = commitMessage;
        this.strategy = strategy;
    }


    public String getSvnUrl() {
        return svnUrl;
    }

    public List<ImportItem> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(ArrayList<ImportItem> items) {
        this.artifacts = items;
    }

    public String getCommitMessage() {
        return commitMessage;
    }

    public void setCommitMessage(String commitMessage) {
        this.commitMessage = commitMessage;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public void setArtifacts(List<ImportItem> artifacts) {
        this.artifacts = artifacts;
    }

    public String getStrategy() {
        return strategy;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Extension
    @Symbol("publishSVN")
    public static final class DescriptorImpl extends BuildStepDescriptor<hudson.tasks.Publisher> {
        public DescriptorImpl() {
            super(Publisher.class);
            load();
        }

        @Override
        public String getDisplayName() {
            return "Publish to Subversion Repository";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public <P extends AbstractProject> FormValidation doCheckCredentialsId(@AncestorInPath Item context, @QueryParameter("svnUrl") final String url, @QueryParameter("credentialsId") final String credentialsId) {
            if ("".equalsIgnoreCase(credentialsId.trim())) {
                return FormValidation.error("credential is not valid");
            }
            try {
                StandardUsernamePasswordCredentials cred = DescriptorImpl.lookupCredentials(url, context, credentialsId);
                Worker worker = new Worker(cred);
                if (worker.getSVNRepository(url) == null) {
                    return FormValidation.error("can not connect to repository");
                }
                worker.getSVNRepository(url).getRepositoryPath("/");
                return FormValidation.ok("connected to repository");
            } catch (SVNException ex) {
                return FormValidation.error(ex.getErrorMessage().getMessage());
            }
        }

        public <P extends AbstractProject> FormValidation doCheckSvnURL(@AncestorInPath Item context, @QueryParameter("svnUrl") final String url, @QueryParameter("credentialsId") final String credentialsId) {
            if ("".equalsIgnoreCase(url.trim())) {
                return FormValidation.error("svn url is not valid");
            }
            return doCheckCredentialsId(context, url, credentialsId);
        }

        /**
         * 提交策略
         *
         * @return
         */
        public ListBoxModel doFillStrategyItems() {
            ListBoxModel items = new ListBoxModel();
            items.add("Always", Constants.ALWAYS_COMMIT);
            items.add("Never", Constants.NEVER_COMMIT);
            items.add("Trigger", Constants.TRIGGER_COMMIT);
            return items;
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context, @QueryParameter String svnUrl) {
            List<DomainRequirement> domainRequirements;
            domainRequirements = URIRequirementBuilder.fromUri(svnUrl.trim()).build();
            return new StandardListBoxModel()
                    .withEmptySelection()
                    .withMatching(
                            CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class)),
                            CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class,
                                    context,
                                    ACL.SYSTEM,
                                    domainRequirements)
                    );
        }

        private static StandardUsernamePasswordCredentials lookupCredentials(String workerUrl, Item context, String credentialsId) {
            return credentialsId == null ? null : CredentialsMatchers
                    .firstOrNull(CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class, context,
                                    ACL.SYSTEM, URIRequirementBuilder.fromUri(workerUrl).build()),
                            CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialsId),
                                    CredentialsMatchers.anyOf(CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class))));
        }
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public void setSvnUrl(String svnUrl) {
        this.svnUrl = svnUrl;
    }

    @Override
    public void perform(@NonNull Run<?, ?> run, @NonNull FilePath workspace, @NonNull EnvVars env, @NonNull Launcher launcher, @NonNull TaskListener listener) throws InterruptedException, IOException {
        if (!workspace.exists()) {
            throw new IOException("workspace: " + workspace.getName() + "not exist");
        }
        StandardUsernamePasswordCredentials credential = getCredentialById(run, credentialsId);
        Worker worker = new Worker(credential, workspace, svnUrl);
        worker.setListener(listener);
        if (Constants.NEVER_COMMIT.equalsIgnoreCase(strategy)) {
            listener.getLogger().println("because of the strategy, no files would be commit");
            worker.dispose();
            return;
        }
        worker.createWorkspace();
        // prepare artifacts to upload repo
        try {
            worker.prepareArtifacts(env, artifacts);
        } catch (SVNPublisherException | SVNException e) {
            worker.dispose();
            throw new InterruptedException("prepare artifacts failed: " + e.getMessage());
        }
        try {
            worker.commit(commitMessage);
        } catch (Throwable e) {
            worker.dispose();
            throw new InterruptedException("commit file failed: " + e.getMessage());
        }
    }



    /**
     * Get credential, according credential ID
     * only support StandardUsernamePasswordCredentials currently,
     * you can make another implementation to support multi credential
     * @param run
     * @param credentialsId
     * @return
     */
    private StandardUsernamePasswordCredentials getCredentialById(@NonNull Run<?, ?> run, @NonNull String credentialsId) {
        List<DomainRequirement> domainRequirements = URIRequirementBuilder.fromUri(svnUrl.trim()).build();
        return CredentialsProvider.findCredentialById(credentialsId, StandardUsernamePasswordCredentials.class, run, domainRequirements);
    }


}
