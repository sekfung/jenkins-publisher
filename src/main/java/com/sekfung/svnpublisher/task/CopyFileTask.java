package com.sekfung.svnpublisher.task;

import com.sekfung.svnpublisher.*;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import jenkins.security.Roles;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.remoting.RoleChecker;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNClientManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

/**
 * @author sekfung
 */
public class CopyFileTask implements FilePath.FileCallable<Void> {

    private Worker worker;
    private ImportItem item;
    private EnvVars envVars;

    public CopyFileTask(Worker worker, ImportItem item, EnvVars envVars) {
        this.worker = worker;
        this.item = item;
        this.envVars = envVars;
    }

    @Override
    public Void invoke(File file, VirtualChannel channel) throws IOException, InterruptedException {
        worker.getListener().getLogger().println("[COPY FILE TASK] executing copy file task....");
        String[] params = item.getParams().split(",");
        //  empty params means it will be always commit
        if (Strategy.ALWAYS_COMMIT.equalsIgnoreCase(worker.getStrategy())) {
            params = new String[]{""};
        }
        if (Strategy.NEVER_COMMIT.equalsIgnoreCase(worker.getStrategy())) {
            return null;
        }
        try {
            SVNClientManager manager = worker.getSVNClientManager();
            FilePath searchFilePath = new FilePath(worker.getProjectSpace(), item.getLocalPath());
            List<File> filesToCopy = Utils.findFilesWithPattern(searchFilePath, item.getPattern(), params, this.envVars);
            worker.getListener().getLogger().printf("[COPY FILE TASK] search dir: %s, pattern: %s\n", searchFilePath.getRemote(), item.getPattern());
            if (filesToCopy.size() == 0) {
                throw new InterruptedException("no files to commit in the dir:" + searchFilePath.getRemote() + ", please check your pattern configuration: " + item.getPattern());
            }
            worker.getListener().getLogger().printf("[COPY FILE TASK] waiting for copying file total: %s\n", filesToCopy.size() + "");
            for (File f : filesToCopy) {
                File workingCopyDir = Paths.get(worker.getWorkspace().getRemote(), item.getPath()).toFile();
                File localFile = Paths.get(worker.getProjectSpace().getRemote(), item.getLocalPath(), f.getName()).toFile();
                File workingCopyFile = new File(workingCopyDir, f.getName());
                if ("false".equalsIgnoreCase(this.item.getParams()) && worker.getStrategy().equalsIgnoreCase("always")) {
                    worker.getListener().getLogger().printf("skip copy file: %s when strategy is always and param trigger is false", localFile.getAbsolutePath());
                    continue;
                }
                FileUtils.copyFile(localFile, workingCopyFile);
                worker.getListener().getLogger().printf("[COPY FILE TASK] Copy file %s, from %s to %s:\n", f.getName(), localFile.getAbsolutePath(), workingCopyDir.getAbsolutePath());
                manager.getWCClient().doAdd(workingCopyFile, true, false, false, SVNDepth.INFINITY, false, false, false);
            }
        } catch (SVNPublisherException | SVNException e) {
            throw new InterruptedException(e.getMessage());
        }
        return null;
    }

    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        checker.check(this, Roles.SLAVE);
    }
}
