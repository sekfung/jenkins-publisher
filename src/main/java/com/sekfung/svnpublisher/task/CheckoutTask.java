package com.sekfung.svnpublisher.task;

import com.sekfung.svnpublisher.Worker;
import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import jenkins.security.Roles;
import org.jenkinsci.remoting.RoleChecker;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.io.File;
import java.io.IOException;

/**
 * @author sekfung
 * @date 2021/12/5
 */
public class CheckoutTask implements FilePath.FileCallable<Void> {
    private Worker worker;

    public CheckoutTask(Worker worker) {
        this.worker = worker;
    }

    @Override
    public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
        worker.getListener().getLogger().println("[CHECKOUT TASK] executing checkout task....");
        SVNClientManager clientManager = worker.getSVNClientManager();
        try {
            clientManager.getUpdateClient().doCheckout(SVNURL.parseURIEncoded(worker.getSvnUrl()), f, SVNRevision.HEAD, SVNRevision.HEAD, SVNDepth.INFINITY, true);
        } catch (SVNException e) {
            throw new InterruptedException("[CHECKOUT TASK] checkout file failed:" + e.getMessage());
        }
        return null;
    }

    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        checker.check(this, Roles.SLAVE);
    }
}
