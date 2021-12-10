package com.sekfung.svnpublisher.task;

import com.sekfung.svnpublisher.Worker;
import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import jenkins.security.Roles;
import org.jenkinsci.remoting.RoleChecker;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNClientManager;

import java.io.File;
import java.io.IOException;

/**
 * @author sekfung
 * @date 2021/12/6
 */
public class AddTask implements FilePath.FileCallable<Void>{

    private Worker worker;

    public AddTask(Worker worker) {
        this.worker = worker;
    }

    @Override
    public Void invoke(File file, VirtualChannel channel) throws IOException, InterruptedException {
        if (!file.exists()) {
            worker.getListener().getLogger().printf("[COPY FILE TASK] file: %s not exist, it will be created\n", file.getAbsolutePath());
        }
        if (!file.mkdirs()) {
            throw new IOException("[COPY FILE TASK] mkdir file failed: " + file.getAbsolutePath());
        }
        worker.getListener().getLogger().println("[COPY FILE TASK] executing add task....\n");
        SVNClientManager clientManager = worker.getSVNClientManager();
        try {
            worker.getListener().getLogger().printf("[COPY FILE TASK] add file into vcs, %s\n", file.getAbsolutePath());
            clientManager.getWCClient().doAdd(file, true, true, false, SVNDepth.INFINITY, false, false, true);
        } catch (SVNException e) {
           throw new InterruptedException("[COPY FILE TASK] add file failed: " + e.getMessage());
        }
        return null;
    }

    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        checker.check(this, Roles.SLAVE);
    }
}
