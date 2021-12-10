package com.sekfung.svnpublisher;

import hudson.FilePath;
import hudson.Util;
import hudson.remoting.VirtualChannel;
import jenkins.security.Roles;
import org.apache.tools.ant.types.FileSet;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author sekfung
 */
public class ListFiles implements FilePath.FileCallable<Map<String, String>>{
    private static final long serialVersionUID = 1;
    private final String includes, excludes;
//        private final boolean defaultExcludes;
//        private final boolean caseSensitive;
//        private final boolean followSymlinks;

    ListFiles(String includes, String excludes) {
        this.includes = includes;
        this.excludes = excludes;
//            this.defaultExcludes = defaultExcludes;
//            this.caseSensitive = caseSensitive;
//            this.followSymlinks = followSymlinks;
    }
    @Override
    public Map<String, String> invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
        Map<String, String> r = new HashMap<>();

        FileSet fileSet = Util.createFileSet(f, includes, excludes);
//            fileSet.setDefaultexcludes(defaultExcludes);
//            fileSet.setCaseSensitive(caseSensitive);
//            fileSet.setFollowSymlinks(followSymlinks);

        for (String file : fileSet.getDirectoryScanner().getIncludedFiles()) {
            file = file.replace(File.separatorChar, '/');
            r.put(file, file);
        }
        return r;
    }

    @Override
    public void checkRoles(RoleChecker checker) throws SecurityException {
        checker.check(this, Roles.SLAVE);
    }
}
