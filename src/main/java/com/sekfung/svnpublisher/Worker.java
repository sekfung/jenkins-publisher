package com.sekfung.svnpublisher;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.sekfung.svnpublisher.task.AddTask;
import com.sekfung.svnpublisher.task.CheckoutTask;
import com.sekfung.svnpublisher.task.CommitTask;
import com.sekfung.svnpublisher.task.CopyFileTask;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.TaskListener;
import org.apache.commons.compress.utils.Lists;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;

/**
 * @author sekfung
 * @date 2021/12/5
 */
public class Worker implements Serializable {

    /**
     * Upload artifacts strategy, default always
     */
    private String strategy = Constants.ALWAYS_COMMIT;
    private String commitMsg;
    private StandardUsernamePasswordCredentials credential;
    private FilePath workspace;
    private FilePath projectSpace;
    private EnvVars envVars;
    private String svnUrl;
    private TaskListener listener;

    public TaskListener getListener() {
        return listener;
    }

    public void setListener(TaskListener listener) {
        this.listener = listener;
    }



    public FilePath getProjectSpace() {
        return projectSpace;
    }

    public void setEnvVars(EnvVars envVars) {
        this.envVars = envVars;
    }

    public void setSvnUrl(String svnUrl) {
        this.svnUrl = svnUrl;
    }

    public String getSvnUrl() {
        return svnUrl;
    }

    public Worker(StandardUsernamePasswordCredentials credential) {
        this.credential = credential;
    }

    public Worker(StandardUsernamePasswordCredentials credential, FilePath baseDir, String svnUrl) {
        this.credential = credential;
        // the sub folder named publisher of project dir is the plugin workspace,
        // waiting for copying artifacts to upload repo
        this.workspace = new FilePath(baseDir, "publisher");
        this.projectSpace = baseDir;
        this.svnUrl = svnUrl;
    }

    public void createWorkspace() throws IOException {
        try {
            if (this.workspace.exists()) {
                cleanWorkspace();
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        try {
            this.workspace.mkdirs();
        } catch (IOException | InterruptedException e) {
            throw new IOException("plugin workspace mkdir error: " + e.getMessage());
        }
    }

    public void prepareArtifacts(EnvVars envVars, List<ImportItem> artifacts) throws SVNPublisherException, IOException, InterruptedException, SVNException {
        if (!workspace.exists()) {
            throw new IOException("plugin workspace not exist");
        }
        prepareWorkDir();
        setEnvVars(envVars);
        SVNRepository repository = getSVNRepository(svnUrl);
        SVNURL svnPath = repository.getLocation();
        artifacts = Utils.parseAndReplaceEnvVars(envVars, cloneItems(artifacts));
        for (ImportItem i : artifacts) {
            SVNURL svnDestination = svnPath.appendPath(i.getPath(), true);
            String path = getRelativePath(svnDestination, repository);
            SVNNodeKind pathType = repository.checkPath(path, repository.getLatestRevision());
            FilePath dir = new FilePath(workspace, i.getPath());
            if (pathType == SVNNodeKind.NONE) {
                dir.act(new AddTask(this));
            }
            FilePath localPath = new FilePath(workspace, i.getLocalPath());
            localPath.act(new CopyFileTask(this, i, envVars));
        }

    }

    private void prepareWorkDir() throws IOException, InterruptedException {
        workspace.act(new CheckoutTask(this));
    }

    private List<ImportItem> cloneItems(List<ImportItem> oldArtifacts) {
        List<ImportItem> newArts = Lists.newArrayList();
        if (oldArtifacts != null) {
            for (ImportItem a : oldArtifacts) {
                newArts.add(new ImportItem(a));
            }
        }
        return newArts;
    }


    public SVNRepository getSVNRepository(String url) throws SVNException {
        SVNRepository repository =  SVNRepositoryFactory.create(SVNURL.parseURIEncoded(url));
        ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(credential.getUsername(), credential.getPassword().getPlainText());
        repository.setAuthenticationManager(authManager);
        return repository;
    }

    private void setup() {
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
        FSRepositoryFactory.setup();
    }

    public SVNClientManager getSVNClientManager() {
        setup();
        DefaultSVNOptions defaultSVNOptions = SVNWCUtil.createDefaultOptions(true);
        return SVNClientManager.newInstance(defaultSVNOptions, credential.getUsername(), credential.getPassword().getPlainText());
    }

    public String getRelativePath(SVNURL repoURL, SVNRepository repository) throws SVNException {
        String repoPath = repoURL.getPath().substring(repository.getRepositoryRoot(true).getPath().length());
        if (!repoPath.startsWith("/")) {
            repoPath = "/" + repoPath;
        }
        return repoPath;
    }

    public void commit(String msg) throws Throwable {
        if (envVars == null) {
            throw new Throwable("env var not found! please call prepare artifacts method before commit");
        }
        String commitMessage = Utils.replaceVars(envVars, msg);
        workspace.act(new CommitTask(this, commitMessage));
    }

    public void dispose() throws IOException {
        cleanWorkspace();
        getSVNClientManager().dispose();
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getCommitMsg() {
        return commitMsg;
    }

    public void setCommitMsg(String commitMsg) {
        this.commitMsg = commitMsg;
    }

    public Credentials getCredential() {
        return credential;
    }

    public void setCredential(StandardUsernamePasswordCredentials credential) {
        this.credential = credential;
    }

    public FilePath getWorkspace() {
        return workspace;
    }

    public void setWorkspace(FilePath workspace) {
        this.workspace = workspace;
    }

    private void cleanWorkspace() throws IOException {
        try {
            if (workspace.exists()) {
                workspace.deleteRecursive();
            }
        } catch (IOException | InterruptedException e) {
            throw new IOException("clean workspace error: " + e.getMessage());
        }
    }

}
