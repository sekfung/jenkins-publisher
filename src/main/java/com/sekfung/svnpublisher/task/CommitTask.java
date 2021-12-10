package com.sekfung.svnpublisher.task;

import com.sekfung.svnpublisher.Worker;
import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import jenkins.security.Roles;
import org.jenkinsci.remoting.RoleChecker;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCommitPacket;

import java.io.File;
import java.io.IOException;

/**
 * @author sekfung
 * @date 2021/12/5
 */
public class CommitTask implements FilePath.FileCallable<Void>{

    private Worker worker;
    private String commitMsg;
    public CommitTask(Worker worker, String commitMsg) {
       this.worker = worker;
       this.commitMsg = commitMsg;
    }

    @Override
    public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
        worker.getListener().getLogger().println("[COMMIT TASK] executing commit task....");
        SVNClientManager svnClientManager = worker.getSVNClientManager();
        SVNCommitPacket item = null;
        try {
            worker.getListener().getLogger().printf("[COMMIT TASK] committing folder, folder path: %s\n", f.getPath());
            item = svnClientManager.getCommitClient().doCollectCommitItems(new File[]{f}, false, true, SVNDepth.INFINITY, null);
            worker.getSVNClientManager().getCommitClient().doCommit(item, false, commitMsg);
            worker.getListener().getLogger().printf("[COMMIT TASK] commit successful, svn url: %s\n", worker.getSvnUrl());
        } catch (SVNException e) {
            worker.getListener().getLogger().printf("[COMMIT TASK] commit folder error: folder name: %s, error msg: %s\n", f.getAbsolutePath(), e.getMessage());
            throw new InterruptedException();
        }
        return null;
    }

    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        checker.check(this, Roles.SLAVE);
    }
}
